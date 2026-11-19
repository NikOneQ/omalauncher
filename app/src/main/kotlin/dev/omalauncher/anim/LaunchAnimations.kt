package dev.omalauncher.anim

import android.app.ActivityOptions
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.window.RemoteTransition
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.LauncherStyle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.ref.WeakReference

/**
 * Omalauncher+: the animation an app opens with from home, by style.toml's animations.style and animations.speed.
 *
 * With CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS, which Android gives only to system apps (Omalauncher+'s KernelSU
 * module makes it one), the launcher plays the opening itself ([LaunchTransition]). Without it the app grows out of the
 * tap with Android's own scale-up, as close as an ordinary app gets.
 */
object LaunchAnimations {
    private const val TAG = "LaunchAnimations"
    const val PERMISSION = "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS"

    /** A tap older than this didn't open the app (a search, a key, a widget's own timer): it then opens from the middle. */
    private const val TAP_MS = 2500L

    @Volatile private var tapX = -1f
    @Volatile private var tapY = -1f
    @Volatile private var tapAt = 0L
    @Volatile private var tapView = WeakReference<View>(null)

    /** Home's touches, so an app grows out of where it was tapped; MainActivity passes each one in. */
    fun onTouch(
        view: View,
        event: MotionEvent,
    ) {
        val a = event.actionMasked
        if (a != MotionEvent.ACTION_DOWN && a != MotionEvent.ACTION_UP) return
        tapX = event.rawX
        tapY = event.rawY
        tapAt = SystemClock.uptimeMillis()
        tapView = WeakReference(view)
    }

    /** Whether the launcher may play openings itself; see the class. */
    fun privileged(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED &&
            hiddenApis

    private val hiddenApis: Boolean by lazy {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            runCatching { HiddenApiBypass.addHiddenApiExemptions("Landroid/window/", "Landroid/view/SurfaceControl", "Landroid/app/ActivityOptions;") }
                .onFailure { Log.w(TAG, "hidden APIs out of reach", it) }
                .getOrDefault(false)
    }

    /**
     * What to start an app with: the options for its opening, or null with [noAnimation] true for "none", or null and
     * false for Android's own ("system").
     */
    fun options(context: Context): Launch {
        val key = LauncherStyle.style[Style.ANIMATION_STYLE]
        if (key == LaunchStyle.NONE) return Launch(null, noAnimation = true)
        val style = LaunchStyle.named(key) ?: return Launch(null, noAnimation = false)
        val speed = LauncherStyle.style[Style.ANIMATION_SPEED].toFloat().coerceIn(0.1f, 5f)
        val density = context.resources.displayMetrics.density
        val origin = origin(context, density)
        if (privileged(context)) {
            runCatching {
                val corner = LauncherStyle.style[Style.ANIMATION_CORNERS].toFloat() * density
                val transition = LaunchTransition(origin, style, speed, corner, context.packageName)
                val remote = RemoteTransition(transition, "OmalauncherPlusLaunch")
                val make = ActivityOptions::class.java.getMethod("makeRemoteTransition", RemoteTransition::class.java)
                return Launch((make.invoke(null, remote) as ActivityOptions).toBundle(), noAnimation = false)
            }.onFailure { Log.w(TAG, "own opening not possible; Android's scale-up instead", it) }
        }
        val view = tapView.get()?.takeIf { it.isAttachedToWindow } ?: return Launch(null, noAnimation = false)
        val at = IntArray(2).also(view::getLocationOnScreen)
        val options =
            ActivityOptions.makeScaleUpAnimation(view, (origin.left - at[0]).toInt(), (origin.top - at[1]).toInt(), origin.width().toInt(), origin.height().toInt())
        return Launch(options.toBundle(), noAnimation = false)
    }

    /** The icon's square around the last tap, or the screen's middle when no tap started this. */
    private fun origin(
        context: Context,
        density: Float,
    ): RectF {
        val size = ICON_DP * density
        val fresh = SystemClock.uptimeMillis() - tapAt < TAP_MS && tapX >= 0
        val metrics = context.resources.displayMetrics
        val x = if (fresh) tapX else metrics.widthPixels / 2f
        val y = if (fresh) tapY else metrics.heightPixels / 2f
        return RectF(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
    }

    private const val ICON_DP = 56f

    class Launch(
        val options: Bundle?,
        val noAnimation: Boolean,
    )
}
