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
) {
    /** A spring with a visible bounce out of the icon; home sinks back and darkens, as on iPhones. */
    IOS("ios", Origin.Icon, Curve.Spring(response = 0.55f, damping = 0.76f), homeZoom = 0.14f, homeAlpha = 0.5f, lead = 0.10f),

    /** Quick and firm, no bounce to speak of, home dimming a little, as on OPPO and OnePlus phones. */
    COLOROS("coloros", Origin.Icon, Curve.Spring(response = 0.30f, damping = 0.97f), homeZoom = 0.08f, homeAlpha = 0.8f, lead = 0.05f),

    /** Material's emphasized easing, no bounce, the app cross-fading in over the icon slowly; home stays put, as on Pixels. */
    PIXEL("pixel", Origin.Icon, Curve.Eased(520, 0.05f, 0.7f, 0.1f, 1f), homeZoom = 0f, homeAlpha = 1f, lead = 0.35f),

    /** Stock One UI: out of the icon on a critically damped spring, home easing back a touch. */
    ONEUI("oneui", Origin.Icon, Curve.Spring(response = 0.42f, damping = 1f), homeZoom = 0.05f, homeAlpha = 0.9f, lead = 0.12f),

    /** The app fades in over home, growing from 86%. */
    FADE("fade", Origin.Screen, Curve.Eased(320, 0.2f, 0f, 0f, 1f), homeZoom = 0f, homeAlpha = 0.6f, lead = 0.86f),

    /** The app slides up from the bottom of the screen. */
    SLIDE("slide", Origin.Slide, Curve.Eased(420, 0.05f, 0.7f, 0.1f, 1f), homeZoom = 0.06f, homeAlpha = 0.8f, lead = 1f),
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
