package dev.omalauncher.anim

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import dev.omalauncher.theme.Style
import dev.omalauncher.ui.LauncherStyle

/**
 * Omalauncher+: how SystemUI gets the launcher's back and close animations ([BackToHome], [CloseToHome]).
 * Omalauncher+'s Vector module, running in SystemUI, calls [METHOD] when the launcher says it's ready ([READY]) and
 * registers what it gets, as the phone's own launcher does through SystemUI; [HANDOFF] passes where its back preview
 * left an app's window, and [CONFIG] whether to show that preview. Only SystemUI gets an answer.
 */
class BackToHomeProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let {
            BackToHome.init(it)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) CloseToHome.init(it)
        }
        return true
    }

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (callingPackage != SYSTEM_UI || !LaunchAnimations.hiddenApis) return null
        val preview = LauncherStyle.style[Style.ANIMATION_BACK_PREVIEW]
        return when (method) {
            METHOD ->
                Bundle().apply {
                    putBoolean("preview", preview)
                    putBinder("callback", BackToHome.callback.asBinder())
                    putBinder("runner", BackToHome.runner.asBinder())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) putBinder("close", CloseToHome.transition.asBinder())
                }
            CONFIG -> Bundle().apply { putBoolean("preview", preview) }
            HANDOFF -> {
                val e = extras ?: return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    CloseToHome.handoff =
                        CloseToHome.Handoff(
                            RectF(e.getFloat("left"), e.getFloat("top"), e.getFloat("right"), e.getFloat("bottom")),
                            e.getFloat("corner"),
                            SystemClock.uptimeMillis(),
                        )
                }
                Bundle()
            }
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        const val METHOD = "backToHome"
        const val HANDOFF = "handoff"
        const val CONFIG = "config"
        const val SYSTEM_UI = "com.android.systemui"

        /** Sent to SystemUI as the launcher starts, so the module asks again (the binders die with the process). */
        const val READY = "dev.omalauncher.plus.BACK_TO_HOME_READY"
    }
}
