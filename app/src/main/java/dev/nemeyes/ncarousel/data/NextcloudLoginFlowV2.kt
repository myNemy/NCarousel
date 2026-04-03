package dev.nemeyes.ncarousel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Implements Nextcloud "Login flow v2" (browser + polling) as described in the official docs:
 * https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2
 */
class NextcloudLoginFlowV2(private val http: OkHttpClient) {

    data class StartResult(
        val loginUrl: String,
        val pollEndpoint: String,
        val pollToken: String,
    )

    data class PollSuccess(
        val server: String,
        val loginName: String,
        val appPassword: String,
    )

    suspend fun start(serverBaseUrl: String): Result<StartResult> = withContext(Dispatchers.IO) {
        runCatching {
            val base = serverBaseUrl.trimEnd('/')
            val url = "$base/index.php/login/v2"
            val req = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("login/v2 failed: HTTP ${res.code}")
                val body = res.body?.string().orEmpty()
                val json = JSONObject(body)
                val poll = json.getJSONObject("poll")
                StartResult(
                    loginUrl = json.getString("login"),
                    pollEndpoint = poll.getString("endpoint").replace("\\/", "/"),
                    pollToken = poll.getString("token"),
                )
            }
        }
    }

    /**
     * Polls until credentials are available or [timeoutMs] elapses.
     * Nextcloud returns 404 until the user approves in the browser.
     */
    suspend fun pollUntilDone(
        pollEndpoint: String,
        token: String,
        timeoutMs: Long = 20L * 60L * 1000L,
        intervalMs: Long = 1500L,
    ): Result<PollSuccess> = withContext(Dispatchers.IO) {
        runCatching { pollUntilSuccess(pollEndpoint, token, timeoutMs, intervalMs) }
    }

    private suspend fun pollUntilSuccess(
        pollEndpoint: String,
        token: String,
        timeoutMs: Long,
        intervalMs: Long,
    ): PollSuccess {
        val deadline = System.currentTimeMillis() + timeoutMs
        val body = "token=$token".toRequestBody("application/x-www-form-urlencoded".toMediaType())
        while (true) {
            if (System.currentTimeMillis() > deadline) error("Login timeout")
            val req = Request.Builder()
                .url(pollEndpoint)
                .post(body)
                .build()
            val done: PollSuccess? = http.newCall(req).execute().use { res ->
                when (res.code) {
                    200 -> {
                        val json = JSONObject(res.body?.string().orEmpty())
                        PollSuccess(
                            server = json.getString("server").replace("\\/", "/").trimEnd('/'),
                            loginName = json.getString("loginName"),
                            appPassword = json.getString("appPassword"),
                        )
                    }
                    404 -> null // Not authorized yet: keep polling.
                    else -> error("Poll failed: HTTP ${res.code}")
                }
            }
            if (done != null) return done
            delay(intervalMs)
        }
    }
}

