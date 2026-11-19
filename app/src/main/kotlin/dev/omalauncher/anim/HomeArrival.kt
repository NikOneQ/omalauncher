package dev.omalauncher.anim

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.LauncherStyle
import kotlin.math.PI

/**
 * Omalauncher+: home coming back after an app, in animations.style's way: it rises from where the opening left it,
 * shrunk, dimmed and blurred, back to itself. Android closes the app window (One UI's gestures do that part); this
 * is the launcher's own half of the return.
 */
object HomeArrival {
    /** Counts the returns; MainActivity bumps it as home comes back into sight. */
    var returns by mutableIntStateOf(0)
        private set

    /** Bumped when a closing transition already brought home forward (CloseToHome): a return under way stops. */
    var settled by mutableIntStateOf(0)
        private set

    private var settledAt = 0L

    fun settle() {
        settledAt = android.os.SystemClock.uptimeMillis()
        settled++
    }

    fun arrived() {
        // A back gesture brought home back and already played its part (BackToHome).
        if (BackToHome.active || android.os.SystemClock.uptimeMillis() - settledAt < 1500) return
        returns++
    }
}

/** Home's tiles as they come back; see [HomeArrival]. */
@Composable
fun Modifier.homeArrival(): Modifier {
    val progress = remember { Animatable(1f) }
    val style = LaunchStyle.named(LauncherStyle.style[Style.ANIMATION_STYLE])
    val speed = LauncherStyle.style[Style.ANIMATION_SPEED].toFloat().coerceIn(0.1f, 5f)
    val blurScale = LauncherStyle.style[Style.ANIMATION_BLUR].toFloat()
    val returns = HomeArrival.returns
    LaunchedEffect(returns) {
        if (returns == 0 || style == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, arrivalSpec(style.curve, speed))
    }
    val settled = HomeArrival.settled
    LaunchedEffect(settled) { if (settled > 0) progress.snapTo(1f) }
    if (style == null) return this
    val blurPx = with(LocalDensity.current) { style.blurDp.dp.toPx() } * blurScale
    return graphicsLayer {
        val q = progress.value
        val left = 1f - q.coerceIn(0f, 1f)
        val scale = 1f - style.homeZoom * (1f - q)
        scaleX = scale
        scaleY = scale
        alpha = 1f - (1f - style.homeAlpha) * left
        val radius = blurPx * left
        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

/** The style's motion as Compose's: its spring by the same response and damping, or its easing over its time. */
private fun arrivalSpec(
    curve: Curve,
    speed: Float,
): AnimationSpec<Float> =
    when (curve) {
        is Curve.Spring -> {
            val omega = 2 * PI / (curve.response / speed)
            spring(dampingRatio = curve.damping, stiffness = (omega * omega).toFloat())
        }
        is Curve.Eased -> tween((curve.ms / speed).toInt(), easing = CubicBezierEasing(curve.x1, curve.y1, curve.x2, curve.y2))
    }
