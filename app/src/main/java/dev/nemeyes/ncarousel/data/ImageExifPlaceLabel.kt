package dev.nemeyes.ncarousel.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import dev.nemeyes.ncarousel.R
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Reads GPS from EXIF, then resolves a place label using enabled geocoders in user order
 * ([CarouselPreferences]), then raw coordinates.
 *
 * Must be called from a background thread.
 */
object ImageExifPlaceLabel {

    fun fromImageBytes(context: Context, bytes: ByteArray, prefs: CarouselPreferences): String {
        val latLong = try {
            ExifInterface(ByteArrayInputStream(bytes)).getLatLong()
        } catch (_: Exception) {
            null
        }
        if (latLong == null || latLong.size < 2) {
            return context.getString(R.string.notify_place_no_gps)
        }
        val lat = latLong[0]
        val lon = latLong[1]

        val http = HttpClientProvider.create(context.applicationContext)
        val lang = Locale.getDefault().toLanguageTag()

        val chain = geocoderBackendsInTryOrder(
            prefs.geocoderOrderMode,
            prefs.geocoderNominatimEnabled,
            prefs.geocoderPlatformEnabled,
            prefs.geocoderPhotonEnabled,
        )
        for (backend in chain) {
            when (backend) {
                GeocoderBackend.NOMINATIM -> {
                    runCatching {
                        NominatimReverseGeocoder.reverse(http, lat, lon, lang)?.trim()?.takeIf { it.isNotEmpty() }
                    }.getOrNull()?.let { return it }
                }
                GeocoderBackend.PLATFORM -> {
                    platformGeocoderLabel(context, lat, lon)?.let { return it }
                }
                GeocoderBackend.PHOTON -> {
                    runCatching {
                        PhotonReverseGeocoder.reverse(http, lat, lon, lang)?.trim()?.takeIf { it.isNotEmpty() }
                    }.getOrNull()?.let { return it }
                }
            }
        }

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
