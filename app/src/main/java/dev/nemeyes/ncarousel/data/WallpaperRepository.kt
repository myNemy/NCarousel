package dev.nemeyes.ncarousel.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
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
     * Decodes [bytes] to a bitmap scaled toward the launcher-reported desired wallpaper size,
     * applies JPEG/EXIF orientation when present, then sets wallpaper per [target] (home / lock / both).
     */
    fun setWallpaperFromImageBytes(
        bytes: ByteArray,
        target: WallpaperTarget = WallpaperTarget.HOME_AND_LOCK,
    ): Result<Unit> = runCatching {
        if (!isSupported()) error("Wallpaper not supported on this device")
        if (!isSetAllowed()) error("App is not allowed to set wallpaper (check device policy)")

        val targetW = max(1, wallpaperManager.desiredMinimumWidth)
        val targetH = max(1, wallpaperManager.desiredMinimumHeight)

        val exifOrientation = readExifOrientation(bytes)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val srcW = options.outWidth
        val srcH = options.outHeight
        val effW: Int
        val effH: Int
        if (exifOrientationSwapsDimensions(exifOrientation)) {
            effW = srcH
            effH = srcW
        } else {
            effW = srcW
            effH = srcH
        }
        options.inSampleSize = computeInSampleSize(effW, effH, targetW, targetH)
        options.inJustDecodeBounds = false

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Unsupported or corrupt image")

        val upright = applyExifOrientation(decoded, exifOrientation)
        val cropped = centerCropToSize(upright, targetW, targetH)
        if (cropped != upright) upright.recycle()

        val which = target.toWallpaperSetFlags()
        wallpaperManager.setBitmap(cropped, null, true, which)
        cropped.recycle()
    }

    private fun readExifOrientation(bytes: ByteArray): Int =
        runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun exifOrientationSwapsDimensions(orientation: Int): Boolean =
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
            -> true
            else -> false
        }

    /**
     * [BitmapFactory] ignores JPEG orientation; apply [TAG_ORIENTATION](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface#TAG_ORIENTATION)
     * so portrait shots are not shown sideways on the wallpaper.
     */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED,
            -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val out = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (out !== bitmap) bitmap.recycle()
        return out
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
