package dev.omalauncher.anim

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle

/**
 * Omalauncher+: how SystemUI gets the launcher's back-to-home animation ([BackToHome]). Omalauncher+'s Vector module,
 * running in SystemUI, calls [METHOD] when the launcher says it's ready ([READY]) and registers what it gets as the
 * home app's back animation, as the phone's own launcher does through SystemUI. Only SystemUI gets an answer.
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
        if (method != METHOD || callingPackage != SYSTEM_UI || !LaunchAnimations.hiddenApis) return null
        return Bundle().apply {
            putBinder("callback", BackToHome.callback.asBinder())
            putBinder("runner", BackToHome.runner.asBinder())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) putBinder("close", CloseToHome.transition.asBinder())
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
        const val SYSTEM_UI = "com.android.systemui"

        /** Sent to SystemUI as the launcher starts, so the module asks again (the binders die with the process). */
        const val READY = "dev.omalauncher.plus.BACK_TO_HOME_READY"
    }
}
