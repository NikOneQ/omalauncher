package dev.omalauncher.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceControl
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowAnimationState
import androidx.annotation.RequiresApi
import java.lang.reflect.Method
import kotlin.math.max
import kotlin.math.min

/**
 * Omalauncher+: plays an app's opening as the launcher, the way a phone's own launcher does: Android hands over the
 * windows of the transition, and each frame moves, scales, crops and rounds them here. The app grows out of [from],
 * the icon tapped (screen pixels), or fades or slides in for the styles that don't start at the icon; home recedes
 * behind it. Needs CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS; see [LaunchAnimations].
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class LaunchTransition(
    /** The icon the app grows out of, or for [closing] flies back into; null finds it by the closing app (LaunchAnimations.originOf). */
    private var from: RectF?,
    private val style: LaunchStyle,
    private val speed: Float,
    /** The window's corner radius in pixels as it reaches full size. */
    private val cornerPx: Float,
    private val ownPackage: String,
    private val density: Float,
    /** animations.blur: how strongly home blurs behind the app, 1 as the style has it. */
    private val blurScale: Float,
    /** Played backwards for an app closing to home (CloseToHome): out of the screen into its icon. */
    private val closing: Boolean = false,
) : IRemoteTransition.Stub() {
    private val main = Handler(Looper.getMainLooper())

    override fun startAnimation(
        token: IBinder?,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        main.post { play(info, t, finishCallback) }
    }

    // Another transition merging into this one: the shell plays it on its own once this one finishes.
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

    private var blur: SurfaceControl? = null
    private var blurPx = 0f

    private class Window(
        val leash: SurfaceControl,
        /** Where the shell put it: its parent-relative offset. */
        val baseX: Float,
        val baseY: Float,
        val width: Int,
        val height: Int,
        /** Its end bounds on screen. */
        val end: Rect,
        /** A whole task, rather than a part of one (an embedded activity's TaskFragment, as WhatsApp opens with on foldables). */
        val isTask: Boolean,
        val pkg: String?,
    )

    private fun play(
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        finish: IRemoteTransitionFinishedCallback,
    ) {
        val opening = mutableListOf<Window>()
        val home = mutableListOf<Window>()
        try {
            for (change in info.changes) {
                val leash = change.leash ?: continue
                val end = change.endAbsBounds
                val offset = change.endRelOffset
                val pkg = change.taskInfo?.baseActivity?.packageName ?: change.taskInfo?.topActivity?.packageName
                val w = Window(leash, offset.x.toFloat(), offset.y.toFloat(), end.width(), end.height(), Rect(end), change.taskInfo != null, pkg)
                val arriving = change.mode == TRANSIT_OPEN || change.mode == TRANSIT_TO_FRONT
                val leaving = change.mode == TRANSIT_CLOSE || change.mode == TRANSIT_TO_BACK
                if (arriving || closing && leaving) t.setVisibility(leash, true)
                when {
                    // Opening: the app arrives over home. Closing: the app leaves, home arrives.
                    !closing && arriving && pkg != ownPackage -> opening += w
                    closing && leaving && pkg != ownPackage -> opening += w
                    pkg == ownPackage && (arriving || leaving) -> home += w
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "transition not understood; shown as it ends", e)
        }
        // The app's task as a whole: its parts (TaskFragments) are inside it and move with it.
        val target = opening.firstOrNull { it.isTask } ?: opening.firstOrNull()
        if (target == null) {
            t.apply()
            done(finish)
            return
        }
        val icon = from ?: LaunchAnimations.originOf(target.pkg) ?: centerIcon(target.end)
        from = icon
        Log.i(TAG, "playing ${style.key}${if (closing) " backwards" else ""} at $speed: ${opening.size} opening, ${home.size} home, ${style.curve.durationMs(speed)} ms")
        // Home blurs behind the app: an effect layer just under its window, over home and the wallpaper.
        val blurPx = style.blurDp * blurScale * density
        val root = runCatching { info.getRoot(0).leash }.getOrNull()
        val blur = if (blurPx > 0 && root != null) effectLayer(root) else null
        blur?.let {
            t.setVisibility(it, true)
            t.setCrop(it, Rect(target.end))
            relativeLayer(t, it, target.leash, -1)
        }
        this.blur = blur
        this.blurPx = blurPx
        frame(t, target, home, if (closing) 1f else 0f)
        t.apply()
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = style.curve.durationMs(speed)
        animator.interpolator = style.curve.interpolator(speed)
        animator.addUpdateListener {
            val tx = SurfaceControl.Transaction()
            val v = it.animatedValue as Float
            frame(tx, target, home, if (closing) 1 - v else v)
            tx.apply()
        }
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val tx = SurfaceControl.Transaction()
                    // A closed app stays hidden until Android takes it away; an opened one is left whole.
                    if (closing) tx.setAlpha(target.leash, 0f) else reset(tx, target)
                    home.forEach { reset(tx, it) }
                    blur?.let { tx.reparent(it, null) }
                    tx.apply()
                    blur?.release()
                    done(finish)
                }
            },
        )
        animator.start()
    }

    /** Puts the windows where they are at progress [p]: 0 at the start, 1 at the end, past 1 when a spring overshoots. */
    private fun frame(
        tx: SurfaceControl.Transaction,
        app: Window,
        home: List<Window>,
        p: Float,
    ) {
        val from = this.from ?: return
        val end = app.end
        if (closing) {
            closingFrame(tx, app, from, 1 - p)
            homeFrame(tx, home, p)
            return
        }
        val rect =
            when (style.origin) {
                LaunchStyle.Origin.Icon ->
                    RectF(lerp(from.left, end.left.toFloat(), p), lerp(from.top, end.top.toFloat(), p), lerp(from.right, end.right.toFloat(), p), lerp(from.bottom, end.bottom.toFloat(), p))
                LaunchStyle.Origin.Screen -> {
                    val s = lerp(style.lead, 1f, p)
                    RectF(end).apply { inset(width() * (1 - s) / 2, height() * (1 - s) / 2) }
                }
                LaunchStyle.Origin.Slide -> RectF(end).apply { offset(0f, (1 - p) * height() * style.lead) }
            }
        // Scaled evenly by width, and cropped to the rect's height around the window's middle.
        val s = max(rect.width() / app.width, 0.01f)
        val cropH = min(app.height.toFloat(), rect.height() / s)
        val cropTop = (app.height - cropH) / 2
        tx.setCrop(app.leash, Rect(0, cropTop.toInt(), app.width, (cropTop + cropH).toInt()))
        tx.setScale(app.leash, s, s)
        tx.setPosition(app.leash, app.baseX + rect.left - end.left, app.baseY + rect.top - end.top - cropTop * s)
        val radius =
            if (style.origin == LaunchStyle.Origin.Icon) lerp(from.width() * ICON_CORNER, cornerPx, min(p, 1f)) else cornerPx
        cornerRadius(tx, app.leash, radius / s)
        if (style.shadowDp > 0) shadowRadius(tx, app.leash, style.shadowDp * density / s)
        tx.setAlpha(
            app.leash,
            when (style.origin) {
                LaunchStyle.Origin.Icon -> min(1f, max(p, 0f) / style.lead)
                else -> min(1f, max(p, 0f))
            },
        )
        homeFrame(tx, home, p)
    }

    /**
     * An app closing, as on iPhones: at [q] 0 it fills the screen; it first draws in to a card, smaller, rounder and a
     * little low, as if pulled already, then flies into its icon and fades into it at the very end.
     */
    private fun closingFrame(
        tx: SurfaceControl.Transaction,
        app: Window,
        icon: RectF,
        q: Float,
    ) {
        val end = RectF(app.end)
        val card = RectF(end).apply {
            inset(width() * (1 - CARD) / 2, height() * (1 - CARD) / 2)
            offset(0f, end.height() * 0.04f)
        }
        val cardCorner = cornerPx * 1.6f
        val rect: RectF
        val radius: Float
        if (q < CARD_SHARE) {
            val f = q / CARD_SHARE
            val e = 1 - (1 - f) * (1 - f)
            rect = lerpRect(end, card, e)
            radius = lerp(0f, cardCorner, e)
        } else {
            val f = (q - CARD_SHARE) / (1 - CARD_SHARE)
            rect = lerpRect(card, icon, f)
            radius = lerp(cardCorner, icon.width() * ICON_CORNER, min(max(f, 0f), 1f))
        }
        val s = max(rect.width() / app.width, 0.01f)
        val cropH = min(app.height.toFloat(), rect.height() / s)
        val cropTop = (app.height - cropH) / 2
        tx.setCrop(app.leash, Rect(0, cropTop.toInt(), app.width, (cropTop + cropH).toInt()))
        tx.setScale(app.leash, s, s)
        tx.setPosition(app.leash, app.baseX + rect.left - app.end.left, app.baseY + rect.top - app.end.top - cropTop * s)
        cornerRadius(tx, app.leash, radius / s)
        if (style.shadowDp > 0) shadowRadius(tx, app.leash, style.shadowDp * density / s)
        tx.setAlpha(app.leash, if (q < 0.82f) 1f else max(0f, 1 - (q - 0.82f) / 0.18f))
    }

    /** Home behind the app at progress [p]: receded, dimmed and blurred as it nears 1. */
    private fun homeFrame(
        tx: SurfaceControl.Transaction,
        home: List<Window>,
        p: Float,
    ) {
        val q = min(max(p, 0f), 1f)
        blur?.let { blurRadius(tx, it, (blurPx * q).toInt()) }
        val zoom = 1 - style.homeZoom * q
        for (h in home) {
            tx.setAlpha(h.leash, lerp(1f, style.homeAlpha, q))
            tx.setScale(h.leash, zoom, zoom)
            tx.setPosition(h.leash, h.baseX + h.width * (1 - zoom) / 2, h.baseY + h.height * (1 - zoom) / 2)
        }
    }

    private fun reset(
        tx: SurfaceControl.Transaction,
        w: Window,
    ) {
        shadowRadius(tx, w.leash, 0f)
        tx.setCrop(w.leash, Rect(0, 0, w.width, w.height))
        tx.setScale(w.leash, 1f, 1f)
        tx.setPosition(w.leash, w.baseX, w.baseY)
        tx.setAlpha(w.leash, 1f)
        cornerRadius(tx, w.leash, 0f)
    }

    private fun done(finish: IRemoteTransitionFinishedCallback) {
        runCatching { finish.onTransitionFinished(null, null) }.onFailure { Log.w(TAG, "finish not delivered", it) }
    }

    companion object {
        private const val TAG = "LaunchTransition"

        // WindowManager's transition modes, hidden from the SDK.
        private const val TRANSIT_OPEN = 1
        private const val TRANSIT_CLOSE = 2
        private const val TRANSIT_TO_FRONT = 3
        private const val TRANSIT_TO_BACK = 4

        /** Where an app with no icon on record flies to: an icon's size in the middle of the screen. */
        private fun centerIcon(b: Rect): RectF {
            val size = b.width() * 0.15f
            return RectF(b.exactCenterX() - size / 2, b.exactCenterY() - size / 2, b.exactCenterX() + size / 2, b.exactCenterY() + size / 2)
        }

        /** A closing app's card: its size against the screen, and the share of the motion spent drawing in to it. */
        private const val CARD = 0.80f
        private const val CARD_SHARE = 0.22f

        private fun lerpRect(
            a: RectF,
            b: RectF,
            f: Float,
        ) = RectF(lerp(a.left, b.left, f), lerp(a.top, b.top, f), lerp(a.right, b.right, f), lerp(a.bottom, b.bottom, f))

        /** An icon's corner radius as a share of its width, about a squircle's. */
        private const val ICON_CORNER = 0.24f

        private val setCornerRadius: Method? by lazy {
            runCatching {
                SurfaceControl.Transaction::class.java.getMethod("setCornerRadius", SurfaceControl::class.java, Float::class.javaPrimitiveType)
            }.onFailure { Log.w(TAG, "no rounded corners", it) }.getOrNull()
        }

        private fun hidden(
            name: String,
            vararg types: Class<*>?,
        ): Method? =
            runCatching { SurfaceControl.Transaction::class.java.getMethod(name, *types) }
                .onFailure { Log.w(TAG, "no $name", it) }
                .getOrNull()

        private val setBackgroundBlurRadius by lazy { hidden("setBackgroundBlurRadius", SurfaceControl::class.java, Int::class.javaPrimitiveType) }
        private val setShadowRadius by lazy { hidden("setShadowRadius", SurfaceControl::class.java, Float::class.javaPrimitiveType) }
        private val setRelativeLayer by lazy { hidden("setRelativeLayer", SurfaceControl::class.java, SurfaceControl::class.java, Int::class.javaPrimitiveType) }

        private fun blurRadius(tx: SurfaceControl.Transaction, sc: SurfaceControl, radius: Int) {
            runCatching { setBackgroundBlurRadius?.invoke(tx, sc, radius) }
        }

        private fun shadowRadius(tx: SurfaceControl.Transaction, sc: SurfaceControl, radius: Float) {
            runCatching { setShadowRadius?.invoke(tx, sc, radius) }
        }

        private fun relativeLayer(tx: SurfaceControl.Transaction, sc: SurfaceControl, relativeTo: SurfaceControl, z: Int) {
            runCatching { setRelativeLayer?.invoke(tx, sc, relativeTo, z) }
        }

        /** A layer that draws nothing but can blur what's under it, in [parent]. */
        private fun effectLayer(parent: SurfaceControl): SurfaceControl? =
            runCatching {
                val b = SurfaceControl.Builder().setName("OmalauncherPlusBlur").setParent(parent)
                SurfaceControl.Builder::class.java.getMethod("setEffectLayer").invoke(b)
                b.build()
            }.onFailure { Log.w(TAG, "no blur layer", it) }.getOrNull()

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
}
