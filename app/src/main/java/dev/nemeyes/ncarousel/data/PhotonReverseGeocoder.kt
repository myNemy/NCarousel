package dev.nemeyes.ncarousel.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

/**
 * Third choice: [Photon](https://photon.komoot.io/) (Komoot public API, OSM-backed), no API key.
 * Used after [NominatimReverseGeocoder] and platform [android.location.Geocoder] when those return nothing.
 *
 * Must be called off the main thread. Intended for occasional wallpaper notifications, not bulk geocoding.
 */
object PhotonReverseGeocoder {

    private const val MAX_LABEL_LEN = 160

    fun reverse(http: OkHttpClient, lat: Double, lon: Double, acceptLanguageTag: String): String? {
        val lang = acceptLanguageTag.substringBefore('-').takeIf { it.length == 2 } ?: Locale.getDefault().language
        val url = "https://photon.komoot.io/reverse"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lon.toString())
            .addQueryParameter("lang", lang.ifEmpty { "en" })
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
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            val feature = features.optJSONObject(0) ?: return null
            val props = feature.optJSONObject("properties") ?: return null
            return labelFromProperties(props)
        }
    }

    private fun labelFromProperties(props: JSONObject): String? {
        val parts = listOfNotNull(
            props.optString("name", "").trim().takeIf { it.isNotEmpty() },
            props.optString("street", "").trim().takeIf { it.isNotEmpty() },
            props.optString("housenumber", "").trim().takeIf { it.isNotEmpty() },
            props.optString("locality", "").trim().takeIf { it.isNotEmpty() },
            props.optString("district", "").trim().takeIf { it.isNotEmpty() },
            props.optString("city", "").trim().takeIf { it.isNotEmpty() },
            props.optString("state", "").trim().takeIf { it.isNotEmpty() },
            props.optString("country", "").trim().takeIf { it.isNotEmpty() },
        ).distinct()
        if (parts.isEmpty()) return null
        return parts.joinToString(", ").take(MAX_LABEL_LEN)
    }
}
