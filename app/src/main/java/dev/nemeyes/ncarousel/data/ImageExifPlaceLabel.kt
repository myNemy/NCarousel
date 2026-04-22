package dev.nemeyes.ncarousel.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import androidx.exifinterface.media.ExifInterface
import dev.nemeyes.ncarousel.R
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Reads GPS from EXIF, then resolves a place label using enabled geocoders in user order
 * ([CarouselPreferences]), then raw coordinates.
 *
 * Must be called from a background thread.
 */
object ImageExifPlaceLabel {

    /** Max time per backend attempt (cap), even if total budget has more remaining. */
    private const val PER_BACKEND_MAX_MS = 5_000L

    private val platformGeocoderExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ncarousel-platform-geocoder").apply { isDaemon = true }
    }

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

        val app = context.applicationContext
        val httpBase = HttpClientProvider.create(app)
        val lang = Locale.getDefault().toLanguageTag()

        val chain = geocoderBackendsInTryOrder(
            prefs.geocoderOrderMode,
            prefs.geocoderNominatimEnabled,
            prefs.geocoderPlatformEnabled,
            prefs.geocoderPhotonEnabled,
        )
        // Dynamic total budget: each enabled backend gets up to [PER_BACKEND_MAX_MS] in sequence.
        // If all backends are disabled, the chain is empty and we fall back immediately.
        val totalBudgetMs = PER_BACKEND_MAX_MS * chain.size.toLong()
        val startMs = System.currentTimeMillis()
        for (backend in chain) {
            val elapsed = System.currentTimeMillis() - startMs
            val remaining = totalBudgetMs - elapsed
            if (remaining <= 0L) break
            val attemptBudget = minOf(remaining, PER_BACKEND_MAX_MS)
            when (backend) {
                GeocoderBackend.NOMINATIM -> {
                    runCatching {
                        val http = httpBase.newBuilder()
                            // callTimeout covers the whole call (connect + TLS + body).
                            .callTimeout(attemptBudget, TimeUnit.MILLISECONDS)
                            // Be conservative on connect; if we can't connect quickly, fall back.
                            .connectTimeout(minOf(attemptBudget, 6_000L), TimeUnit.MILLISECONDS)
                            .readTimeout(minOf(attemptBudget, 8_000L), TimeUnit.MILLISECONDS)
                            .build()
                        NominatimReverseGeocoder.reverse(http, lat, lon, lang)?.trim()?.takeIf { it.isNotEmpty() }
                    }.getOrNull()?.let { return it }
                }
                GeocoderBackend.PLATFORM -> {
                    platformGeocoderLabelWithTimeout(context, lat, lon, attemptBudget)?.let { return it }
                }
                GeocoderBackend.PHOTON -> {
                    runCatching {
                        val http = httpBase.newBuilder()
                            .callTimeout(attemptBudget, TimeUnit.MILLISECONDS)
                            .connectTimeout(minOf(attemptBudget, 6_000L), TimeUnit.MILLISECONDS)
                            .readTimeout(minOf(attemptBudget, 8_000L), TimeUnit.MILLISECONDS)
                            .build()
                        PhotonReverseGeocoder.reverse(http, lat, lon, lang)?.trim()?.takeIf { it.isNotEmpty() }
                    }.getOrNull()?.let { return it }
                }
            }
        }

        return coordsOnly(context, lat, lon)
    }

    /** [Geocoder] backend is device-dependent; on GMS builds it is typically Google. */
    private fun platformGeocoderLabelWithTimeout(context: Context, lat: Double, lon: Double, timeoutMs: Long): String? {
        if (!Geocoder.isPresent()) return null
        val budget = timeoutMs.coerceAtLeast(250L)
        val future = platformGeocoderExecutor.submit<String?> {
            try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(lat, lon, 1)
                val addr = list?.firstOrNull() ?: return@submit null
                addressToLabelOrNull(addr)
            } catch (_: Exception) {
                null
            }
        }
        return try {
            future.get(budget, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            future.cancel(true)
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
