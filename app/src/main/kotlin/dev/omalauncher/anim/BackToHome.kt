package dev.omalauncher.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.IRemoteAnimationFinishedCallback
import android.view.IRemoteAnimationRunner
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.window.BackMotionEvent
import android.window.IBackAnimationHandoffHandler
import android.window.IOnBackInvokedCallback
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.LauncherStyle
import java.lang.reflect.Method
import kotlin.math.max
import kotlin.math.min

/**
 * Omalauncher+: the back gesture that leaves an app for home, played by the launcher as a phone's own launcher plays
 * it. While the finger moves the app's window shrinks and follows it; let go and it flies back into the icon it
 * opened from in animations.style's way, or back to full size if the gesture was cancelled.
 *
 * Android hands this to the launcher SystemUI knows as the home app (setBackToLauncherCallback), which for a launcher
 * other than the phone's own only Omalauncher+'s Vector module does: it asks [BackToHomeProvider] for [callback] and
 * [runner]. Without it Android plays its own back animation.
 */
object BackToHome {
    private const val TAG = "BackToHome"
    private val main = Handler(Looper.getMainLooper())

    private var context: Context? = null
    private var app: RemoteAnimationTarget? = null
    private var home: RemoteAnimationTarget? = null
    private var finished: IRemoteAnimationFinishedCallback? = null
    private var progress = 0f
    private var startY = 0f
    private var touchY = 0f
    private var edge = 0
    private var settling: ValueAnimator? = null

    /** Whether a back gesture to home is under way: home then doesn't play its own return ([HomeArrival]). */
    @Volatile var active = false
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
        LaunchAnimations.hiddenApis
    }

    val callback: IOnBackInvokedCallback by lazy {
        object : IOnBackInvokedCallback.Stub() {
            override fun onBackStarted(e: BackMotionEvent) {
                val y = e.touchY
                main.post {
                    active = true
                    settling?.cancel()
                    progress = 0f
                    startY = y
                    touchY = y
                    edge = e.swipeEdge
                }
            }

            override fun onBackProgressed(e: BackMotionEvent) {
                val p = e.progress
                val y = e.touchY
                main.post {
                    progress = p
                    touchY = y
                    follow()
                }
            }

            override fun onBackCancelled() {
                main.post { settle(home = false) }
            }

            override fun onBackInvoked() {
                main.post { settle(home = true) }
            }

            override fun setTriggerBack(triggerBack: Boolean) {}

            override fun setHandoffHandler(handler: IBackAnimationHandoffHandler?) {}
        }
    }

    val runner: IRemoteAnimationRunner by lazy {
        object : IRemoteAnimationRunner.Stub() {
            override fun onAnimationStart(
                transit: Int,
                apps: Array<RemoteAnimationTarget>?,
                wallpapers: Array<RemoteAnimationTarget>?,
                nonApps: Array<RemoteAnimationTarget>?,
                finishedCallback: IRemoteAnimationFinishedCallback,
            ) {
                main.post {
                    val own = context?.packageName
                    val all = apps.orEmpty()
                    app = all.firstOrNull { it.mode == RemoteAnimationTarget.MODE_CLOSING && it.packageName() != own }
                        ?: all.firstOrNull { it.mode == RemoteAnimationTarget.MODE_CLOSING }
                    home = all.firstOrNull { it.mode == RemoteAnimationTarget.MODE_OPENING }
                    finished = finishedCallback
                    Log.i(TAG, "back to home from ${app?.packageName()}: ${all.size} targets")
                    follow()
                }
            }

            override fun onAnimationCancelled() {
                main.post { finish() }
            }
        }
    }

    private fun RemoteAnimationTarget.packageName(): String? = taskInfo?.baseActivity?.packageName ?: taskInfo?.topActivity?.packageName

    private val style get() = LaunchStyle.named(LauncherStyle.style[Style.ANIMATION_STYLE]) ?: LaunchStyle.ONEUI

    private val density get() = context?.resources?.displayMetrics?.density ?: 3f

    /** The window while the finger moves: smaller as the gesture goes on, nudged away from the edge and after the finger. */
    private fun follow() {
        val app = app ?: return
        val b = app.screenSpaceBounds ?: return
        val s = 1f - (0.10f + style.homeZoom * 0.6f) * progress
        val dx = b.width() * 0.05f * progress * (if (edge == 0) 1 else -1)
        val dy = ((touchY - startY) * 0.25f).coerceIn(-b.height() * 0.08f, b.height() * 0.08f)
        val left = b.left + b.width() * (1 - s) / 2 + dx
        val top = b.top + b.height() * (1 - s) / 2 + dy
        val tx = SurfaceControl.Transaction()
        place(tx, app, RectF(left, top, left + b.width() * s, top + b.height() * s), cornerPx() * min(1f, progress * 3), 1f)
        home?.let { placeHome(tx, it, progress) }
        tx.apply()
    }

    /** Let go: into the icon for [home], or back to the whole screen. */
    private fun settle(home: Boolean) {
        val app = app
        val b = app?.screenSpaceBounds
        if (app == null || b == null) {
            if (!home) active = false
            finish()
            return
        }
        val s = 1f - (0.10f + style.homeZoom * 0.6f) * progress
        val dx = b.width() * 0.05f * progress * (if (edge == 0) 1 else -1)
        val dy = ((touchY - startY) * 0.25f).coerceIn(-b.height() * 0.08f, b.height() * 0.08f)
        val left = b.left + b.width() * (1 - s) / 2 + dx
        val top = b.top + b.height() * (1 - s) / 2 + dy
        val from = RectF(left, top, left + b.width() * s, top + b.height() * s)
        val icon = LaunchAnimations.originOf(app.packageName()) ?: centerIcon(b)
        val to = if (home) icon else RectF(b)
        val fromCorner = cornerPx() * min(1f, progress * 3)
        val toCorner = if (home) icon.width() * 0.24f else 0f
        val startProgress = progress
        val speed = LauncherStyle.style[Style.ANIMATION_SPEED].toFloat().coerceIn(0.1f, 5f)
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = style.curve.durationMs(speed)
        animator.interpolator = style.curve.interpolator(speed)
        animator.addUpdateListener {
            val f = it.animatedValue as Float
            val tx = SurfaceControl.Transaction()
            val r = RectF(lerp(from.left, to.left, f), lerp(from.top, to.top, f), lerp(from.right, to.right, f), lerp(from.bottom, to.bottom, f))
            val alpha = if (home) (if (f < 0.7f) 1f else max(0f, 1f - (f - 0.7f) / 0.3f)) else 1f
            place(tx, app, r, lerp(fromCorner, toCorner, min(f, 1f)), alpha)
            this.home?.let { h -> placeHome(tx, h, if (home) lerp(startProgress, 1f, min(f, 1f)) else lerp(startProgress, 0f, min(f, 1f))) }
            tx.apply()
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    settling = null
                    if (!home) active = false
                    finish()
                }
            },
        )
        settling = animator
        animator.start()
    }

    private fun finish() {
        val done = finished
        app = null
        home = null
        finished = null
        progress = 0f
        // Home is in front again once Android has it; HomeArrival skips this return.
        main.postDelayed({ active = false }, 600)
        runCatching { done?.onAnimationFinished() }.onFailure { Log.w(TAG, "finish not delivered", it) }
    }

    /** The window at [r] on screen: scaled evenly by width, cropped to the rect's height around its middle, rounded. */
    private fun place(
        tx: SurfaceControl.Transaction,
        target: RemoteAnimationTarget,
        r: RectF,
        cornerPx: Float,
        alpha: Float,
    ) {
        val b = target.screenSpaceBounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val s = max(r.width() / w, 0.01f)
        val cropH = min(h, r.height() / s)
        val cropTop = (h - cropH) / 2
        val base = target.position
        tx.setCrop(target.leash, Rect(0, cropTop.toInt(), w.toInt(), (cropTop + cropH).toInt()))
        tx.setScale(target.leash, s, s)
        tx.setPosition(target.leash, (base?.x ?: 0) + r.left - b.left, (base?.y ?: 0) + r.top - b.top - cropTop * s)
        tx.setAlpha(target.leash, alpha)
        cornerRadius(tx, target.leash, cornerPx / s)
    }

    /** Home behind, coming forward from the style's receded, dimmed state as [p] goes to 1. */
    private fun placeHome(
        tx: SurfaceControl.Transaction,
        target: RemoteAnimationTarget,
        p: Float,
    ) {
        val b = target.screenSpaceBounds
        val q = p.coerceIn(0f, 1f)
        val zoom = 1 - style.homeZoom * (1 - q)
        val base = target.position
        tx.setScale(target.leash, zoom, zoom)
        tx.setPosition(target.leash, (base?.x ?: 0) + b.width() * (1 - zoom) / 2, (base?.y ?: 0) + b.height() * (1 - zoom) / 2)
        tx.setAlpha(target.leash, lerp(style.homeAlpha, 1f, q))
    }

    private fun cornerPx() = LauncherStyle.style[Style.ANIMATION_CORNERS].toFloat() * density

    private fun centerIcon(b: Rect): RectF {
        val size = 56f * density
        return RectF(b.exactCenterX() - size / 2, b.exactCenterY() - size / 2, b.exactCenterX() + size / 2, b.exactCenterY() + size / 2)
    }

    private val setCornerRadius: Method? by lazy {
        runCatching { SurfaceControl.Transaction::class.java.getMethod("setCornerRadius", SurfaceControl::class.java, Float::class.javaPrimitiveType) }.getOrNull()
    }

    private fun cornerRadius(
        tx: SurfaceControl.Transaction,
        leash: SurfaceControl,
        radius: Float,
    ) {
        runCatching { setCornerRadius?.invoke(tx, leash, radius) }
    }

    private fun lerp(
        a: Float,
        b: Float,
        p: Float,
    ) = a + (b - a) * p
}
