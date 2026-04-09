package dev.nemeyes.ncarousel.data

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.exifinterface.media.ExifInterface
import dev.nemeyes.ncarousel.R
import java.io.ByteArrayInputStream

/**
 * Estrae un sottoinsieme leggibile di tag EXIF da bytes immagine (JPEG/PNG/WebP/HEIF dove supportato).
 * Le etichette seguono la lingua di sistema tramite [Resources].
 */
object ImageExifSummary {

    fun formatLines(res: Resources, bytes: ByteArray): List<String> {
        val exif = try {
            ExifInterface(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            return listOf(res.getString(R.string.exif_err_cannot_open))
        }

        val lines = mutableListOf<String>()

        fun add(@StringRes labelRes: Int, tag: String) {
            exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { v ->
                lines += res.getString(R.string.exif_field_line, res.getString(labelRes), v)
            }
        }

        add(R.string.exif_label_datetime_original, ExifInterface.TAG_DATETIME_ORIGINAL)
        if (lines.none { it.contains(res.getString(R.string.exif_label_datetime_original)) }) {
            add(R.string.exif_label_datetime, ExifInterface.TAG_DATETIME)
        }
        add(R.string.exif_label_make, ExifInterface.TAG_MAKE)
        add(R.string.exif_label_model, ExifInterface.TAG_MODEL)
        add(R.string.exif_label_exposure, ExifInterface.TAG_EXPOSURE_TIME)
        add(R.string.exif_label_aperture, ExifInterface.TAG_F_NUMBER)
        add(R.string.exif_label_iso, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        add(R.string.exif_label_focal, ExifInterface.TAG_FOCAL_LENGTH)
        add(R.string.exif_label_focal_35mm, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
        dimensionsLine(res, exif)?.let { lines += it }
        orientationLine(res, exif)?.let { lines += it }
        add(R.string.exif_label_software, ExifInterface.TAG_SOFTWARE)
        add(R.string.exif_label_artist, ExifInterface.TAG_ARTIST)
        add(R.string.exif_label_copyright, ExifInterface.TAG_COPYRIGHT)

        exif.latLong?.let { (lat, lon) ->
            lines += res.getString(R.string.exif_gps_line, lat, lon)
        }

        if (lines.isEmpty()) {
            lines += res.getString(R.string.exif_none_found)
        }
        return lines
    }

    private fun dimensionsLine(res: Resources, exif: ExifInterface): String? {
        val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            ?: exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION)
        val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            ?: exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION)
        if (w.isNullOrBlank() && h.isNullOrBlank()) return null
        return res.getString(R.string.exif_dimensions_px, w ?: "?", h ?: "?")
    }

    private fun orientationLine(res: Resources, exif: ExifInterface): String? {
        val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        if (o == ExifInterface.ORIENTATION_UNDEFINED) return null
        val label = when (o) {
            ExifInterface.ORIENTATION_NORMAL -> res.getString(R.string.exif_orientation_normal)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> res.getString(R.string.exif_orientation_flip_h)
            ExifInterface.ORIENTATION_ROTATE_180 -> res.getString(R.string.exif_orientation_rot180)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> res.getString(R.string.exif_orientation_flip_v)
            ExifInterface.ORIENTATION_TRANSPOSE -> res.getString(R.string.exif_orientation_transpose)
            ExifInterface.ORIENTATION_ROTATE_90 -> res.getString(R.string.exif_orientation_rot90)
            ExifInterface.ORIENTATION_TRANSVERSE -> res.getString(R.string.exif_orientation_transverse)
            ExifInterface.ORIENTATION_ROTATE_270 -> res.getString(R.string.exif_orientation_rot270)
            else -> null
        } ?: return res.getString(R.string.exif_orientation_line, o.toString())
        return res.getString(R.string.exif_orientation_line, label)
    }
}
