package dev.nemeyes.ncarousel.data.ocs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * OCS capabilities, including [ThemingColors] from the theming app — same source as the official Nextcloud client.
 *
 * https://docs.nextcloud.com/server/latest/developer_manual/client_apis/OCS/ocs-api-overview.html
 */
class OcsCapabilitiesClient(private val http: OkHttpClient) {

    data class ThemingColors(
        val color: String,
        val colorText: String?,
    )

    suspend fun fetchTheming(
        serverBaseUrl: String,
        loginName: String?,
        appPassword: String?,
    ): Result<ThemingColors> = withContext(Dispatchers.IO) {
        val base = serverBaseUrl.trimEnd('/')
        val url = "$base/ocs/v1.php/cloud/capabilities?format=json"

        fun parseTheming(json: JSONObject): ThemingColors? {
            val ocs = json.optJSONObject("ocs") ?: return null
            val data = ocs.optJSONObject("data") ?: return null
            val capabilities = data.optJSONObject("capabilities") ?: return null
            val theming = capabilities.optJSONObject("theming") ?: return null
            val color = theming.optString("color", "").trim()
            if (color.isEmpty()) return null
            val colorText = theming.optString("color-text", "").trim().takeIf { it.isNotEmpty() }
                ?: theming.optString("colorText", "").trim().takeIf { it.isNotEmpty() }
            return ThemingColors(color = color, colorText = colorText)
        }

        fun call(useBasicAuth: Boolean): ThemingColors? {
            val builder = Request.Builder()
                .url(url)
                .header("OCS-APIRequest", "true")
                .get()
            if (useBasicAuth && !loginName.isNullOrBlank() && !appPassword.isNullOrBlank()) {
                builder.header("Authorization", Credentials.basic(loginName, appPassword))
            }
            http.newCall(builder.build()).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
                return parseTheming(json)
            }
        }

        runCatching {
            call(useBasicAuth = false)
                ?: if (!loginName.isNullOrBlank() && !appPassword.isNullOrBlank()) {
                    call(useBasicAuth = true)
                } else {
                    null
                }
                ?: error("Theming non disponibile (capabilities senza theming o richiesta non riuscita).")
        }
    }
}
