package dev.omalauncher.anim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.omalauncher.theme.ThemeStore

/**
 * Omalauncher+: home's background, small and already blurred, for SystemUI's back preview to show around a shrinking
 * app instead of black (Android shows nothing behind an app that handles back itself). Small enough to pass in a
 * Bundle; scaled up on screen its blur stays soft. Made again only when the background changes.
 */
object Backdrop {
    private const val WIDTH = 96
    private var key: String? = null
    private var cached: Bitmap? = null

    @Synchronized
    fun bitmap(context: Context): Bitmap? {
        val file = runCatching { ThemeStore.savedSelection(context)?.second }.getOrNull() ?: return null
        val k = "${file.path}|${file.lastModified()}"
        if (k == key) return cached
        val made =
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= WIDTH * 2) sample *= 2
                val big = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
                val small = Bitmap.createScaledBitmap(big, WIDTH, (WIDTH.toLong() * big.height / big.width).toInt().coerceAtLeast(1), true)
                if (small !== big) big.recycle()
                blurred(small, radius = 3, passes = 3)
            }.getOrNull()
        key = k
        cached = made
        return made
    }

    /** A box blur, a few passes of it: close to a Gaussian, and quick at this size. */
    private fun blurred(
        bitmap: Bitmap,
        radius: Int,
        passes: Int,
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        var px = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        repeat(passes) {
            px = pass(px, w, h, radius, horizontal = true)
            px = pass(px, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pass(
        src: IntArray,
        w: Int,
        h: Int,
        r: Int,
        horizontal: Boolean,
    ): IntArray {
        val out = IntArray(src.size)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var rs = 0
                var gs = 0
                var bs = 0
                var n = 0
                for (d in -r..r) {
                    val j = (i + d).coerceIn(0, len - 1)
                    val c = if (horizontal) src[line * w + j] else src[j * w + line]
                    rs += (c shr 16) and 0xff
                    gs += (c shr 8) and 0xff
                    bs += c and 0xff
                    n++
                }
                val at = if (horizontal) line * w + i else i * w + line
                out[at] = (0xff shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
            }
        }
        return out
    }
}
