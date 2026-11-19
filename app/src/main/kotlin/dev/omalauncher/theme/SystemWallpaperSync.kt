package dev.omalauncher.theme

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File

/**
 * Omalauncher+: keeps Android's wallpaper the launcher's background (style.toml's wallpaper.system), so the phone's
 * own screens (lock screen, recents, an app opening over home) show what home shows and nothing flashes between the
 * two. The home screen's is set only while our live wallpaper isn't Android's: that one already follows the theme.
 * Each background is set once; [sync] remembers the last in [prefs].
 */
internal object SystemWallpaperSync {
    private const val TAG = "SystemWallpaperSync"
    private const val SYNCED = "system_wallpaper_synced"

    /** Sets [file] as Android's wallpaper unless it already is; blocking, so off the main thread. */
    fun sync(
        context: Context,
        prefs: SharedPreferences,
        file: File?,
        liveWallpaperActive: Boolean,
    ) {
        if (file == null || !file.isFile) return
        val stamp = "${file.path}|${file.lastModified()}|${file.length()}|$liveWallpaperActive"
        if (prefs.getString(SYNCED, null) == stamp) return
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) return
            val which = if (liveWallpaperActive) WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            file.inputStream().use { wm.setStream(it, null, true, which) }
            prefs.edit().putString(SYNCED, stamp).apply()
        }.onFailure { Log.w(TAG, "Android's wallpaper not set to $file", it) }
    }
}
