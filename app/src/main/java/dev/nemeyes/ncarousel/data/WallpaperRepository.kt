package dev.nemeyes.ncarousel.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Applies a bitmap as system wallpaper using [WallpaperManager] (see
 * [WallpaperManager reference](https://developer.android.com/reference/android/app/WallpaperManager)).
 *
 * Sizing uses the physical display (not the launcher’s often 2× [desiredMinimumWidth] parallax
 * canvas) so home and lock share the same framing across OEMs. Callers that use
 * [WallpaperTarget.HOME_AND_LOCK] should schedule [dev.nemeyes.ncarousel.work.HomeWallpaperResync]
 * *after* persisting the last-applied href.
 */
class WallpaperRepository(private val context: Context) {

    private val app = context.applicationContext
    private val wallpaperManager: WallpaperManager =
        WallpaperManager.getInstance(app)

    fun isSupported(): Boolean = wallpaperManager.isWallpaperSupported

    fun isSetAllowed(): Boolean = wallpaperManager.isSetWallpaperAllowed

    /**
     * Decodes [bytes] to a bitmap scaled to the display size, applies JPEG/EXIF orientation when
     * present, then sets wallpaper per [target] (home / lock / both) using [cropMode]
     * (or [CarouselPreferences.wallpaperCropMode] when null).
     */
    fun setWallpaperFromImageBytes(
        bytes: ByteArray,
        target: WallpaperTarget = WallpaperTarget.HOME_AND_LOCK,
        cropMode: WallpaperCropMode? = null,
    ): Result<Unit> = runCatching {
        if (!isSupported()) error("Wallpaper not supported on this device")
        if (!isSetAllowed()) error("App is not allowed to set wallpaper (check device policy)")

        val mode = cropMode ?: CarouselPreferences(app).wallpaperCropMode
        val (targetW, targetH) = resolveTargetBitmapSize()

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
        val framed = layoutToSize(upright, targetW, targetH, mode)
        if (framed != upright) upright.recycle()

        try {
            applyBitmapToTargets(framed, target)
            snapshotAppliedWallpaperIds()
        } finally {
            framed.recycle()
        }
    }

    /** Record system/lock wallpaper ids so OEM resync can no-op when nothing changed. */
    private fun snapshotAppliedWallpaperIds() {
        val systemId = runCatching {
            wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        }.getOrDefault(0)
        val lockId = runCatching {
            wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        }.getOrDefault(0)
        LastAppliedWallpaperStore.setAppliedWallpaperIds(app, systemId, lockId)
    }

    /**
     * Prefer physical display size so home and lock match. [WallpaperManager.desiredMinimumWidth]
     * is often ~2× screen width for scrolling wallpapers; using it alone misaligns lock on many
     * OEMs. If desired is unset (≤0), platform docs also say to use the display size—never 1×1.
     */
    private fun resolveTargetBitmapSize(): Pair<Int, Int> {
        val (sw, sh) = screenSizePx()
        // Always cover the physical panel; do not expand to the parallax virtual canvas.
        return max(1, sw) to max(1, sh)
    }

    @Suppress("DEPRECATION")
    private fun screenSizePx(): Pair<Int, Int> {
        val wm = app.getSystemService(WindowManager::class.java) ?: return 1080 to 1920
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            max(1, bounds.width()) to max(1, bounds.height())
        } else {
            val size = Point()
            wm.defaultDisplay.getRealSize(size)
            max(1, size.x) to max(1, size.y)
        }
    }

    private fun applyBitmapToTargets(cropped: Bitmap, target: WallpaperTarget) {
        // Full-frame hint: OEMs otherwise invent different crops for home vs lock.
        val hint = Rect(0, 0, cropped.width, cropped.height)
        when (target) {
            WallpaperTarget.HOME_AND_LOCK -> {
                // Separate calls: combined FLAG_SYSTEM|FLAG_LOCK is unreliable on several OEMs.
                // Home first, then lock last so a SYSTEM write cannot leave lock empty.
                var homeOk = setWallpaperChecked(cropped, hint, WallpaperManager.FLAG_SYSTEM)
                var lockOk = setWallpaperChecked(cropped, hint, WallpaperManager.FLAG_LOCK)
                if (!homeOk) {
                    homeOk = setWallpaperChecked(cropped, hint, WallpaperManager.FLAG_SYSTEM)
                }
                if (!lockOk) {
                    lockOk = setWallpaperChecked(cropped, hint, WallpaperManager.FLAG_LOCK)
                }
                if (!lockOk || !homeOk) {
                    // Last resort: combined flags (helps devices that only honor the pair).
                    val both = setWallpaperChecked(
                        cropped,
                        hint,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                    )
                    if (!both && !homeOk && !lockOk) {
                        error("Failed to set home and lock wallpaper")
                    }
                    if (!homeOk && !both) error("Failed to set home wallpaper")
                    if (!lockOk && !both) error("Failed to set lock wallpaper")
                }
            }
            else -> {
                val which = target.toWallpaperSetFlags()
                if (!setWallpaperChecked(cropped, hint, which)) {
                    if (!setWallpaperChecked(cropped, hint, which)) {
                        error("Failed to set wallpaper")
                    }
                }
            }
        }
    }

    /**
     * [WallpaperManager.setBitmap] return meaning varies by API/OEM (wallpaper id vs flag mask).
     * Treat `0` as failure; any non-zero as success.
     */
    private fun setWallpaperChecked(bitmap: Bitmap, visibleCropHint: Rect, which: Int): Boolean =
        runCatching {
            wallpaperManager.setBitmap(bitmap, visibleCropHint, /* allowBackup = */ true, which) != 0
        }.getOrDefault(false)

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

    private fun layoutToSize(
        src: Bitmap,
        dstW: Int,
        dstH: Int,
        mode: WallpaperCropMode,
    ): Bitmap = when (mode) {
        WallpaperCropMode.COVER -> centerCropToSize(src, dstW, dstH)
        WallpaperCropMode.FIT -> fitCenterToSize(src, dstW, dstH)
        WallpaperCropMode.CENTER -> centerNoUpscaleToSize(src, dstW, dstH)
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

    /** Scales uniformly to fit inside [dstW]×[dstH]; letterboxes with black. */
    private fun fitCenterToSize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width <= 0 || src.height <= 0) return src
        val scale = min(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val sw = max(1, (src.width * scale).roundToInt()).coerceAtMost(dstW)
        val sh = max(1, (src.height * scale).roundToInt()).coerceAtMost(dstH)
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(
            scaled,
            ((dstW - sw) / 2f),
            ((dstH - sh) / 2f),
            null,
        )
        if (scaled != src) scaled.recycle()
        return out
    }

    /**
     * No upscale: if the image is larger than the screen, center-crop at 1:1; if smaller, center
     * on a black canvas. If only one side overflows, scale down just enough to fit (no upscale).
     */
    private fun centerNoUpscaleToSize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        if (src.width <= 0 || src.height <= 0) return src
        if (src.width >= dstW && src.height >= dstH) {
            val x = ((src.width - dstW) / 2f).roundToInt().coerceIn(0, src.width - dstW)
            val y = ((src.height - dstH) / 2f).roundToInt().coerceIn(0, src.height - dstH)
            return Bitmap.createBitmap(src, x, y, dstW, dstH)
        }
        val scale = min(
            1f,
            min(dstW.toFloat() / src.width, dstH.toFloat() / src.height),
        )
        val sw = max(1, (src.width * scale).roundToInt()).coerceAtMost(dstW)
        val sh = max(1, (src.height * scale).roundToInt()).coerceAtMost(dstH)
        val drawn = if (scale < 1f - 1e-4f) {
            Bitmap.createScaledBitmap(src, sw, sh, true)
        } else {
            src
        }
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(
            drawn,
            ((dstW - sw) / 2f),
            ((dstH - sh) / 2f),
            null,
        )
        if (drawn != src) drawn.recycle()
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
