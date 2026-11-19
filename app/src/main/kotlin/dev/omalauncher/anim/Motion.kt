package dev.omalauncher.anim

import android.animation.TimeInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Omalauncher+: how an app opens from home, by name in style.toml's animations.style. [origin] is where the window
 * comes from: the icon tapped (it grows out of it with rounded corners), or the screen itself (it fades or slides in).
 */
enum class LaunchStyle(
    val key: String,
    val origin: Origin,
    /** The window's motion, before animations.speed. */
    val curve: Curve,
    /** How far home recedes behind the opening app: 0.08 shrinks it to 92%. */
    val homeZoom: Float,
    /** How visible home stays behind it, 1 not dimmed at all. */
    val homeAlpha: Float,
    /** For [Origin.Icon], the share of the motion over which the window fades in over the icon; for the others, where it starts: its scale (Screen) or how far below (Slide, in screen heights). */
    val lead: Float,
    /** How much home blurs behind the app, in dp at full strength (animations.blur 1). */
    val blurDp: Float,
    /** The shadow under the opening window, in dp. */
    val shadowDp: Float,
) {
    /** iOS 27: a spring with a visible bounce out of the icon, home sinking back, darkening and blurring behind it like Liquid Glass. */
    IOS("ios", Origin.Icon, Curve.Spring(response = 0.55f, damping = 0.76f), homeZoom = 0.14f, homeAlpha = 0.55f, lead = 0.10f, blurDp = 30f, shadowDp = 28f),

    /** ColorOS 17: quick and firm, no bounce to speak of, home dimming and blurring a little behind a lifted window. */
    COLOROS("coloros", Origin.Icon, Curve.Spring(response = 0.30f, damping = 0.97f), homeZoom = 0.08f, homeAlpha = 0.8f, lead = 0.05f, blurDp = 18f, shadowDp = 16f),

    /** Android 17 on Pixels: Material 3 Expressive's bouncy spring, the app cross-fading in over the icon slowly, home staying put while it blurs, as Beta 3 added. */
    PIXEL("pixel", Origin.Icon, Curve.Spring(response = 0.50f, damping = 0.72f), homeZoom = 0f, homeAlpha = 0.85f, lead = 0.35f, blurDp = 24f, shadowDp = 10f),

    /** Stock One UI: out of the icon on a critically damped spring, home easing back a touch. */
    ONEUI("oneui", Origin.Icon, Curve.Spring(response = 0.42f, damping = 1f), homeZoom = 0.05f, homeAlpha = 0.9f, lead = 0.12f, blurDp = 8f, shadowDp = 12f),

    /** The app fades in over home, growing from 86%. */
    FADE("fade", Origin.Screen, Curve.Eased(320, 0.2f, 0f, 0f, 1f), homeZoom = 0f, homeAlpha = 0.6f, lead = 0.86f, blurDp = 16f, shadowDp = 0f),

    /** The app slides up from the bottom of the screen. */
    SLIDE("slide", Origin.Slide, Curve.Eased(420, 0.05f, 0.7f, 0.1f, 1f), homeZoom = 0.06f, homeAlpha = 0.8f, lead = 1f, blurDp = 12f, shadowDp = 20f),
    ;

    enum class Origin { Icon, Screen, Slide }

    companion object {
        /** Not one of the styles: "system" leaves it to Android, "none" opens apps at once. */
        const val SYSTEM = "system"
        const val NONE = "none"
        const val DEFAULT = "ios"

        fun named(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** A motion from 0 to 1, possibly past 1 and back for a spring, over [durationMs]. */
sealed interface Curve {
    fun durationMs(speed: Float): Long

    fun interpolator(speed: Float): TimeInterpolator

    /**
     * A damped spring like UIKit's: [response] is roughly the seconds one swing takes, [damping] 1 for no bounce and
     * less for some. It settles when within 0.2% of the end.
     */
    data class Spring(
        val response: Float,
        val damping: Float,
    ) : Curve {
        private fun omega(speed: Float) = (2 * PI / (response / speed)).toFloat()

        override fun durationMs(speed: Float): Long {
            val w = omega(speed)
            return (ln(500.0) / (damping * w) * 1000).toLong().coerceIn(120, 1600)
        }

        override fun interpolator(speed: Float): TimeInterpolator {
            val w = omega(speed)
            val seconds = durationMs(speed) / 1000f
            val z = damping
            return TimeInterpolator { f ->
                val t = f * seconds
                if (z < 1f) {
                    val wd = w * sqrt(1 - z * z)
                    (1 - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))).toFloat()
                } else {
                    (1 - exp(-w * t) * (1 + w * t)).toFloat()
                }
            }
        }
    }

    /** A cubic Bézier easing over a fixed time. */
    data class Eased(
        val ms: Int,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : Curve {
        override fun durationMs(speed: Float) = (ms / speed).toLong().coerceIn(60, 3000)

        override fun interpolator(speed: Float): TimeInterpolator = PathInterpolator(x1, y1, x2, y2)
    }
}
