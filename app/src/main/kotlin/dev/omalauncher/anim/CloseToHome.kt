package dev.omalauncher.anim

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.SurfaceControl
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowAnimationState
import androidx.annotation.RequiresApi
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.LauncherStyle

/**
 * Omalauncher+: an app closing to home (back out of its last screen, Home on the buttons) flies into its icon in
 * animations.style's way: [LaunchTransition] played backwards. SystemUI's Vector module registers this for the
 * transitions where an app closes and home comes to the front, as the phone's own launcher registers its own; it
 * gets it from [BackToHomeProvider]. "none" closes at once; "system" plays like "oneui".
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object CloseToHome {
    private const val TAG = "CloseToHome"
    private var context: Context? = null

    /**
     * Where SystemUI's back preview left the app's window as the finger let go (screen pixels, its corner radius and when):
     * the closing that follows starts there instead of from the full screen.
     */
    @Volatile var handoff: Handoff? = null

    class Handoff(
        val rect: android.graphics.RectF,
        val cornerPx: Float,
        val at: Long,
    ) {
        /** Whether the closing it belongs to is the one starting now. */
        val fresh get() = android.os.SystemClock.uptimeMillis() - at < 1500
    }

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    val transition: IRemoteTransition by lazy {
        object : IRemoteTransition.Stub() {
            override fun startAnimation(
                token: IBinder?,
                info: TransitionInfo,
                t: SurfaceControl.Transaction,
                finishCallback: IRemoteTransitionFinishedCallback,
            ) {
                val ctx = context
                val key = LauncherStyle.style[Style.ANIMATION_STYLE]
                if (ctx == null || key == LaunchStyle.NONE) {
                    t.apply()
                    runCatching { finishCallback.onTransitionFinished(null, null) }
                    return
                }
                val style = LaunchStyle.named(key) ?: LaunchStyle.ONEUI
                val density = ctx.resources.displayMetrics.density
                val speed = LauncherStyle.style[Style.ANIMATION_SPEED].toFloat().coerceIn(0.1f, 5f)
                val corner = LauncherStyle.style[Style.ANIMATION_CORNERS].toFloat() * density
                val blur = LauncherStyle.style[Style.ANIMATION_BLUR].toFloat()
                Log.i(TAG, "app closing to home, as $key")
                android.os.Handler(android.os.Looper.getMainLooper()).post { HomeArrival.settle() }
                val held = handoff?.takeIf { it.fresh }
                handoff = null
                Log.i(TAG, "app closing to home, as $key, from ${if (held != null) "the back preview" else "the full screen"}")
                // From under the finger the window is already on its way: the rest goes quicker.
                val pace = if (held != null) speed * 1.35f else speed
                LaunchTransition(null, style, pace, corner, ctx.packageName, density, blur, closing = true, handoffRect = held?.rect, handoffCorner = held?.cornerPx ?: 0f)
                    .startAnimation(token, info, t, finishCallback)
            }

            override fun mergeAnimation(
                transition: IBinder?,
                info: TransitionInfo?,
                t: SurfaceControl.Transaction?,
                mergeTarget: IBinder?,
                finishCallback: IRemoteTransitionFinishedCallback?,
            ) {}

            override fun takeOverAnimation(
                transition: IBinder?,
                info: TransitionInfo?,
                startTransaction: SurfaceControl.Transaction?,
                finishCallback: IRemoteTransitionFinishedCallback?,
                states: Array<out WindowAnimationState>?,
            ) {}

            override fun onTransitionConsumed(
                transition: IBinder?,
                aborted: Boolean,
            ) {}
        }
    }
}
