package dev.nemeyes.ncarousel.data.ocs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Minimal OCS client used only to resolve the canonical user id (UID) for WebDAV paths.
 *
 * Docs mention fetching actual username from `/ocs/v1.php/cloud/user`.
 */
class OcsUserClient(private val http: OkHttpClient) {

    suspend fun fetchUserId(serverBaseUrl: String, loginName: String, appPassword: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = serverBaseUrl.trimEnd('/')
                val url = "$base/ocs/v1.php/cloud/user?format=json"
                val req = Request.Builder()
                    .url(url)
                    .header("OCS-APIRequest", "true")
                    .header("Authorization", Credentials.basic(loginName, appPassword))
                    .get()
                    .build()
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) error("OCS user failed: HTTP ${res.code}")
                    val json = JSONObject(res.body?.string().orEmpty())
                    val ocs = json.getJSONObject("ocs")
                    val data = ocs.getJSONObject("data")
                    val id = data.getString("id")
                    if (id.isBlank()) error("Missing user id")
                    id
                }
            }
        }
}

