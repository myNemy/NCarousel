package dev.nemeyes.ncarousel.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalizes a user-typed Nextcloud base URL for Login Flow v2 / status.php.
 * Adds https:// when the scheme is missing; rejects non-https.
 */
object NextcloudServerUrl {
    fun normalizeHttpsBase(raw: String): Result<String> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "empty" }
        val withScheme = when {
            trimmed.contains("://") -> trimmed
            else -> "https://$trimmed"
        }
        val url = withScheme.trimEnd('/').toHttpUrlOrNull()
            ?: error("invalid")
        require(url.scheme.equals("https", ignoreCase = true)) { "https_required" }
        require(url.host.isNotBlank()) { "invalid" }
        // Drop path/query for the server base (status.php and login/v2 live on the host root).
        // Keep non-default port.
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host(url.host)
        if (url.port != 443 && url.port != -1) {
            builder.port(url.port)
        }
        // Preserve a path prefix if the instance is in a subdirectory (e.g. /nextcloud).
        val pathSegments = url.pathSegments.filter { it.isNotBlank() }
        if (pathSegments.isNotEmpty()) {
            pathSegments.forEach { builder.addPathSegment(it) }
        }
        builder.build().toString().trimEnd('/')
    }
}
