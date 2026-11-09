package dev.nemeyes.ncarousel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Lightweight reachability check via public [status.php](https://github.com/nextcloud/server/blob/master/status.php).
 * Used only to validate the typed server URL before Login Flow v2 / manual credentials.
 */
class NextcloudServerProbe(private val http: OkHttpClient) {

    data class Ok(
        val productName: String,
        val versionString: String,
        val normalizedBaseUrl: String,
    )

    sealed class Failure {
        data object HttpsRequired : Failure()
        data object InvalidUrl : Failure()
        data object Unreachable : Failure()
        data object NotNextcloud : Failure()
        data object NotInstalled : Failure()
        data object Maintenance : Failure()
        data class Http(val code: Int) : Failure()
    }

    sealed class Outcome {
        data class Success(val ok: Ok) : Outcome()
        data class Error(val failure: Failure) : Outcome()
    }

    suspend fun probe(rawServerUrl: String): Outcome = withContext(Dispatchers.IO) {
        val normalized = NextcloudServerUrl.normalizeHttpsBase(rawServerUrl).getOrElse { e ->
            val msg = e.message.orEmpty()
            return@withContext Outcome.Error(
                when (msg) {
                    "https_required" -> Failure.HttpsRequired
                    else -> Failure.InvalidUrl
                },
            )
        }
        try {
            val req = Request.Builder()
                .url("$normalized/status.php")
                .get()
                .header("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext Outcome.Error(Failure.Http(res.code))
                }
                val body = res.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!json.has("installed") || !json.has("versionstring")) {
                    return@withContext Outcome.Error(Failure.NotNextcloud)
                }
                if (!json.optBoolean("installed", false)) {
                    return@withContext Outcome.Error(Failure.NotInstalled)
                }
                if (json.optBoolean("maintenance", false)) {
                    return@withContext Outcome.Error(Failure.Maintenance)
                }
                val product = json.optString("productname").ifBlank { "Nextcloud" }
                val version = json.optString("versionstring").ifBlank {
                    json.optString("version")
                }
                Outcome.Success(
                    Ok(
                        productName = product,
                        versionString = version,
                        normalizedBaseUrl = normalized,
                    ),
                )
            }
        } catch (_: org.json.JSONException) {
            Outcome.Error(Failure.NotNextcloud)
        } catch (_: Exception) {
            Outcome.Error(Failure.Unreachable)
        }
    }
}
