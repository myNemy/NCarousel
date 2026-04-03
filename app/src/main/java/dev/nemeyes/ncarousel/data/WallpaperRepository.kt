package dev.nemeyes.ncarousel.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Applies a bitmap as system wallpaper using [WallpaperManager] (see
 * [WallpaperManager reference](https://developer.android.com/reference/android/app/WallpaperManager)).
 */
class WallpaperRepository(private val context: Context) {

    private val wallpaperManager: WallpaperManager =
        WallpaperManager.getInstance(context)

    fun isSupported(): Boolean = wallpaperManager.isWallpaperSupported

    fun isSetAllowed(): Boolean = wallpaperManager.isSetWallpaperAllowed

    /**
     * Decodes [bytes] to a bitmap scaled toward the launcher-reported desired wallpaper size, then
     * sets home (and lock screen on API 24+) wallpaper.
     */
    fun setWallpaperFromImageBytes(bytes: ByteArray): Result<Unit> = runCatching {
        if (!isSupported()) error("Wallpaper not supported on this device")
        if (!isSetAllowed()) error("App is not allowed to set wallpaper (check device policy)")

        val targetW = max(1, wallpaperManager.desiredMinimumWidth)
        val targetH = max(1, wallpaperManager.desiredMinimumHeight)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = computeInSampleSize(options.outWidth, options.outHeight, targetW, targetH)
        options.inJustDecodeBounds = false

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Unsupported or corrupt image")

        val cropped = centerCropToSize(decoded, targetW, targetH)
        if (cropped != decoded) decoded.recycle()

        val which = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        } else {
            WallpaperManager.FLAG_SYSTEM
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setBitmap(cropped, null, true, which)
        } else {
            @Suppress("DEPRECATION")
            wallpaperManager.setBitmap(cropped)
        }
        cropped.recycle()
    }

    /** Scales uniformly to cover [dstW]×[dstH] then crops the center (similar to “crop” fill). */
    private fun centerCropToSize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width <= 0 || src.height <= 0) return src
        val scale = max(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val sw = max(1, (src.width * scale).roundToInt())
        val sh = max(1, (src.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val x = ((sw - dstW) / 2f).roundToInt().coerceIn(0, max(0, sw - dstW))
        val y = ((sh - dstH) / 2f).roundToInt().coerceIn(0, max(0, sh - dstH))
        val cw = dstW.coerceAtMost(sw)
        val ch = dstH.coerceAtMost(sh)
        val out = Bitmap.createBitmap(scaled, x, y, cw, ch)
        if (scaled != src) scaled.recycle()
        return out
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        if (srcW <= 0 || srcH <= 0) return 1
        var inSampleSize = 1
        if (srcH > reqH || srcW > reqW) {
            val halfH = srcH / 2
            val halfW = srcW / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }
}
