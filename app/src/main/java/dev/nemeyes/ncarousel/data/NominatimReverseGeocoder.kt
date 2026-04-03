package dev.nemeyes.ncarousel.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Primary reverse geocoding via [OpenStreetMap Nominatim](https://nominatim.org/release-docs/develop/api/Reverse/).
 * Further fallbacks: platform [android.location.Geocoder], then [PhotonReverseGeocoder].
 *
 * Must be called off the main thread. Use sparingly (public instance policy: no bulk; wallpaper changes are fine).
 */
object NominatimReverseGeocoder {

    private const val MAX_LABEL_LEN = 160

    fun reverse(http: OkHttpClient, lat: Double, lon: Double, acceptLanguage: String): String? {
        val url = "https://nominatim.openstreetmap.org/reverse"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("format", "json")
            .addQueryParameter("accept-language", acceptLanguage)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val body = res.body?.string()?.trim().orEmpty()
            if (body.isEmpty()) return null
            return parseLabel(JSONObject(body))
        }
    }

    private fun parseLabel(json: JSONObject): String? {
        val display = json.optString("display_name", "").trim()
        if (display.isNotEmpty()) {
            return display.take(MAX_LABEL_LEN).trimEnd(',')
        }
        val addr = json.optJSONObject("address") ?: return null
        val parts = listOfNotNull(
            addr.optString("road", "").trim().takeIf { it.isNotEmpty() },
            addr.optString("suburb", "").trim().takeIf { it.isNotEmpty() },
            addr.optString("city", "").trim().takeIf { it.isNotEmpty() }
                ?: addr.optString("town", "").trim().takeIf { it.isNotEmpty() }
                ?: addr.optString("village", "").trim().takeIf { it.isNotEmpty() },
            addr.optString("state", "").trim().takeIf { it.isNotEmpty() },
            addr.optString("country", "").trim().takeIf { it.isNotEmpty() },
        ).distinct()
        if (parts.isEmpty()) return null
        return parts.joinToString(", ").take(MAX_LABEL_LEN)
    }
}
