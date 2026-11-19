package dev.omalauncher.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.omalauncher.R
import dev.omalauncher.Runtime
import dev.omalauncher.anim.LaunchAnimations
import dev.omalauncher.anim.SystemActions
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.ConfirmDialog
import dev.omalauncher.ui.Mono
import dev.omalauncher.ui.OverlayPanel
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.ReadingWidth
import dev.omalauncher.ui.TextSize
import dev.omalauncher.ui.card
import kotlinx.coroutines.launch

/** Omalauncher+: animations.style's values, by their name and what they look like. */
private val OPEN_STYLES =
    listOf(
        Triple("ios", R.string.style_animation_ios, R.string.anim_ios_hint),
        Triple("coloros", R.string.style_animation_coloros, R.string.anim_coloros_hint),
        Triple("pixel", R.string.style_animation_pixel, R.string.anim_pixel_hint),
        Triple("oneui", R.string.style_animation_oneui, R.string.anim_oneui_hint),
        Triple("fade", R.string.style_animation_fade, R.string.anim_fade_hint),
        Triple("slide", R.string.style_animation_slide, R.string.anim_slide_hint),
        Triple("system", R.string.style_animation_system, R.string.anim_system_style_hint),
        Triple("none", R.string.style_animation_none, R.string.anim_none_hint),
    )

/** animations.back-style's values. */
private val BACK_STYLES =
    listOf(
        Triple("card", R.string.anim_back_card, R.string.anim_back_card_hint),
        Triple("slide", R.string.anim_back_slide, R.string.anim_back_slide_hint),
        Triple("subtle", R.string.anim_back_subtle, R.string.anim_back_subtle_hint),
        Triple("follow", R.string.anim_back_follow, R.string.anim_back_follow_hint),
    )

private enum class Restart(
    @StringRes val title: Int,
    @StringRes val message: Int,
) {
    SystemUi(R.string.anim_restart_systemui, R.string.anim_restart_systemui_message),
    Launcher(R.string.anim_restart_launcher, R.string.anim_restart_launcher_message),
    SoftReboot(R.string.anim_soft_reboot, R.string.anim_soft_reboot_message),
}

/**
 * Omalauncher+: everything about motion in one place: how apps open and close, the back gesture, and the restarts
 * that changes to the system modules need.
 */
@Composable
fun AnimationsScreen(
    runtime: Runtime,
    onDismiss: () -> Unit,
) {
    val style = runtime.style
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privileged = remember { LaunchAnimations.privileged(context) }
    var restart by remember { mutableStateOf<Restart?>(null) }
    var failed by remember { mutableStateOf(false) }
    OverlayPanel(stringResource(R.string.anim_title), onDismiss, contentWidth = ReadingWidth) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BasicText(
                stringResource(if (privileged) R.string.anim_status_full else R.string.anim_status_simple),
                Modifier.card().padding(2.dp),
                style = Mono.copy(color = if (privileged) Palette.green else Palette.named("yellow") ?: Palette.accent),
            )

            Section(R.string.anim_open_title, R.string.anim_open_hint)
            Choices(OPEN_STYLES, style.style[Style.ANIMATION_STYLE]) { key -> style.update { it.with(Style.ANIMATION_STYLE, key) } }
            Spacer(Modifier.height(8.dp))
            NumberSlider(R.string.style_animation_speed, style, Style.ANIMATION_SPEED)
            NumberSlider(R.string.style_animation_blur, style, Style.ANIMATION_BLUR)
            Stepper(R.string.style_animation_corners, style, Style.ANIMATION_CORNERS)

            Section(R.string.anim_back_title, R.string.anim_back_hint)
            Flag(R.string.style_animation_back_preview, R.string.style_on, R.string.style_off, style, Style.ANIMATION_BACK_PREVIEW)
            if (style.style[Style.ANIMATION_BACK_PREVIEW]) {
                Choices(BACK_STYLES, style.style[Style.ANIMATION_BACK_STYLE]) { key -> style.update { it.with(Style.ANIMATION_BACK_STYLE, key) } }
                Spacer(Modifier.height(8.dp))
                NumberSlider(R.string.anim_back_strength, style, Style.ANIMATION_BACK_STRENGTH)
            }

            Section(R.string.anim_system_title, R.string.anim_system_hint)
            for (r in Restart.entries) {
                Action(stringResource(r.title)) { restart = r }
                Spacer(Modifier.height(8.dp))
            }
            if (failed) BasicText(stringResource(R.string.anim_restart_failed), style = Mono.copy(color = Palette.red, fontSize = TextSize.Small))
            Spacer(Modifier.height(24.dp))
        }
    }
    restart?.let { r ->
        ConfirmDialog(stringResource(r.title), stringResource(r.message), stringResource(R.string.anim_restart_confirm), onDismiss = { restart = null }) {
            restart = null
            scope.launch {
                failed =
                    !when (r) {
                        Restart.SystemUi -> SystemActions.restartSystemUi()
                        Restart.Launcher -> SystemActions.restartLauncher(context)
                        Restart.SoftReboot -> SystemActions.softReboot()
                    }
            }
        }
    }
}

/** A section's title and a line on what it's about. */
@Composable
private fun Section(
    @StringRes title: Int,
    @StringRes hint: Int,
) {
    Spacer(Modifier.height(24.dp))
    BasicText(stringResource(title), style = Mono.copy(color = Palette.accent, fontSize = TextSize.Large))
    Spacer(Modifier.height(4.dp))
    BasicText(stringResource(hint), style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
    Spacer(Modifier.height(12.dp))
}

/** One card per value: its name and what it looks like; the chosen one marked in the accent color. */
@Composable
private fun Choices(
    choices: List<Triple<String, Int, Int>>,
    current: String,
    onPick: (String) -> Unit,
) {
    for ((key, name, hint) in choices) {
        val picked = key == current
        Row(Modifier.card(onClick = { onPick(key) }), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(stringResource(name), style = Mono.copy(color = if (picked) Palette.accent else Palette.foreground))
                Spacer(Modifier.height(2.dp))
                BasicText(stringResource(hint), style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
            }
            Spacer(Modifier.width(12.dp))
            BasicText(if (picked) "●" else "○", style = Mono.copy(color = if (picked) Palette.accent else Palette.muted))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Action(
    label: String,
    onClick: () -> Unit,
) {
    Row(Modifier.card(onClick = onClick).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = Mono)
        BasicText("›", style = Mono.copy(color = Palette.muted))
    }
}
