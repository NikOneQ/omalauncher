package dev.omalauncher.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.omalauncher.R
import dev.omalauncher.storage.MoveFromOmalauncher
import dev.omalauncher.ui.ConfirmDialog
import kotlinx.coroutines.launch

/**
 * Omalauncher+: confirms moving Omalauncher's data into this launcher, then starts [MoveFromOmalauncher], which stops
 * this app and opens it again when done. Without root it says so and stays open.
 */
@Composable
internal fun MoveFromOmalauncherDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }
    var noRoot by remember { mutableStateOf(false) }
    // The notification's texts, read here: the move runs after this screen is gone.
    val noticeMoving = stringResource(R.string.plus_move_notice_moving)
    val noticeDone = stringResource(R.string.plus_move_notice_done)
    val noticeFailed = stringResource(R.string.plus_move_notice_failed)
    ConfirmDialog(
        stringResource(R.string.plus_move_confirm_title),
        stringResource(if (noRoot) R.string.plus_move_no_root else R.string.plus_move_confirm_message),
        stringResource(if (waiting) R.string.plus_move_waiting else R.string.plus_move_confirm),
        onDismiss = onDismiss,
    ) {
        if (waiting) return@ConfirmDialog
        waiting = true
        noRoot = false
        scope.launch {
            val started =
                MoveFromOmalauncher.start(
                    context,
                    moving = noticeMoving,
                    done = noticeDone,
                    failed = noticeFailed,
                )
            waiting = false
            noRoot = !started
            // Started: the move stops this app in a moment and says how it went in a notification.
            if (started) onDismiss()
        }
    }
}
