package dev.nemeyes.ncarousel.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import dev.nemeyes.ncarousel.R
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Reads GPS coordinates from image EXIF (JPEG/WebP where supported) and resolves a short place
 * label via [Geocoder], similar to desktop gallery apps showing a geographic name.
 *
 * Must be called from a background thread ([Geocoder] may hit network).
 */
object ImageExifPlaceLabel {

    fun fromImageBytes(context: Context, bytes: ByteArray): String {
        val latLong = FloatArray(2)
        val hasGps = try {
            ExifInterface(ByteArrayInputStream(bytes)).getLatLong(latLong)
        } catch (_: Exception) {
            false
        }
        if (!hasGps) {
            return context.getString(R.string.notify_place_no_gps)
        }
        val lat = latLong[0].toDouble()
        val lon = latLong[1].toDouble()
        if (!Geocoder.isPresent()) {
            return coordsOnly(context, lat, lon)
        }
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull()
            if (addr != null) {
                addressToShortLabel(context, addr, lat, lon)
            } else {
                coordsOnly(context, lat, lon)
            }
        } catch (_: Exception) {
            coordsOnly(context, lat, lon)
        }
    }

    private fun coordsOnly(context: Context, lat: Double, lon: Double): String =
        String.format(
            Locale.getDefault(),
            context.getString(R.string.notify_place_coords_only),
            lat,
            lon,
        )

    private fun addressToShortLabel(context: Context, a: Address, lat: Double, lon: Double): String {
        val line0 = (0..a.maxAddressLineIndex)
            .mapNotNull { i -> a.getAddressLine(i)?.trim()?.takeIf { it.isNotEmpty() } }
            .firstOrNull()
        if (!line0.isNullOrBlank()) return line0
        val parts = listOfNotNull(
            a.featureName?.takeIf { it.isNotBlank() && it != a.thoroughfare },
            a.locality?.takeIf { it.isNotBlank() },
            a.adminArea?.takeIf { it.isNotBlank() },
            a.countryName?.takeIf { it.isNotBlank() },
        ).distinct()
        if (parts.isNotEmpty()) return parts.joinToString(", ")
        return coordsOnly(context, lat, lon)
    }
}
