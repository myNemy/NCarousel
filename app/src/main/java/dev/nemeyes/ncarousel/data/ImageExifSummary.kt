package dev.nemeyes.ncarousel.data

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Estrae un sottoinsieme leggibile di tag EXIF da bytes immagine (JPEG/PNG/WebP/HEIF dove supportato).
 */
object ImageExifSummary {

    fun formatLines(bytes: ByteArray): List<String> {
        val exif = try {
            ExifInterface(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            return listOf("Impossibile aprire i metadati EXIF da questi dati.")
        }

        val lines = mutableListOf<String>()

        fun add(label: String, tag: String) {
            exif.getAttribute(tag)?.trim()?.takeIf { it.isNotEmpty() }?.let { lines += "$label: $it" }
        }

        add("Data/ora (originale)", ExifInterface.TAG_DATETIME_ORIGINAL)
        if (lines.none { it.startsWith("Data/ora") }) {
            add("Data/ora", ExifInterface.TAG_DATETIME)
        }
        add("Marca", ExifInterface.TAG_MAKE)
        add("Modello", ExifInterface.TAG_MODEL)
        add("Esposizione", ExifInterface.TAG_EXPOSURE_TIME)
        add("Apertura (f)", ExifInterface.TAG_F_NUMBER)
        add("ISO", ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
        add("Lunghezza focale", ExifInterface.TAG_FOCAL_LENGTH)
        add("Lunghezza focale 35mm", ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
        dimensionsLine(exif)?.let { lines += "Dimensioni: $it" }
        orientationLine(exif)?.let { lines += it }
        add("Software", ExifInterface.TAG_SOFTWARE)
        add("Artista", ExifInterface.TAG_ARTIST)
        add("Copyright", ExifInterface.TAG_COPYRIGHT)

        exif.latLong?.let { (lat, lon) ->
            lines += String.format(Locale.getDefault(), "GPS: %.5f°, %.5f°", lat, lon)
        }

        if (lines.isEmpty()) {
            lines += "Nessun metadato EXIF rilevato in questa immagine."
        }
        return lines
    }

    private fun dimensionsLine(exif: ExifInterface): String? {
        val w = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            ?: exif.getAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION)
        val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            ?: exif.getAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION)
        if (w.isNullOrBlank() && h.isNullOrBlank()) return null
        return "${w ?: "?"} × ${h ?: "?"} px"
    }

    private fun orientationLine(exif: ExifInterface): String? {
        val o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        if (o == ExifInterface.ORIENTATION_UNDEFINED) return null
        val label = when (o) {
            ExifInterface.ORIENTATION_NORMAL -> "Normale"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Capovolto orizzontale"
            ExifInterface.ORIENTATION_ROTATE_180 -> "180°"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Capovolto verticale"
            ExifInterface.ORIENTATION_TRANSPOSE -> "Trasposto"
            ExifInterface.ORIENTATION_ROTATE_90 -> "90°"
            ExifInterface.ORIENTATION_TRANSVERSE -> "Trasverso"
            ExifInterface.ORIENTATION_ROTATE_270 -> "270°"
            else -> null
        } ?: return "Orientamento: $o"
        return "Orientamento: $label"
    }
}
