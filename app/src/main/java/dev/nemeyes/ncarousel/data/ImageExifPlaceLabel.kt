package dev.nemeyes.ncarousel.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import dev.nemeyes.ncarousel.R
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Reads GPS from EXIF, then resolves a place label:
 * 1. [NominatimReverseGeocoder] (OpenStreetMap)
 * 2. platform [Geocoder] (often Google on devices with Play services)
 * 3. [PhotonReverseGeocoder] (Komoot public instance, OSM-backed)
 * 4. raw coordinates string
 *
 * Must be called from a background thread.
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

        val http = HttpClientProvider.create(context.applicationContext)
        val lang = Locale.getDefault().toLanguageTag()

        runCatching {
            NominatimReverseGeocoder.reverse(http, lat, lon, lang)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()?.let { return it }

        platformGeocoderLabel(context, lat, lon)?.let { return it }

        runCatching {
            PhotonReverseGeocoder.reverse(http, lat, lon, lang)?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()?.let { return it }

        return coordsOnly(context, lat, lon)
    }

    /** [Geocoder] backend is device-dependent; on GMS builds it is typically Google. */
    private fun platformGeocoderLabel(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull() ?: return null
            addressToLabelOrNull(addr)
        } catch (_: Exception) {
            null
        }
    }

    private fun addressToLabelOrNull(a: Address): String? {
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
        if (parts.isEmpty()) return null
        return parts.joinToString(", ")
    }

    private fun coordsOnly(context: Context, lat: Double, lon: Double): String =
        String.format(
            Locale.getDefault(),
            context.getString(R.string.notify_place_coords_only),
            lat,
            lon,
        )
}
