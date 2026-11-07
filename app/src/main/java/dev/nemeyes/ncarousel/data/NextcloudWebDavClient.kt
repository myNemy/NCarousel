package dev.nemeyes.ncarousel.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
/**
 * Minimal WebDAV client for Nextcloud following the official overview:
 * [Nextcloud WebDAV basic APIs](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html).
 *
 * Authenticated files live under `remote.php/dav/files/{userId}/...`.
 * Basic Auth uses [basicAuthUser] (login / email); the DAV path uses [davUserId] (OCS uid), which can differ.
 */
class NextcloudWebDavClient(
    private val http: okhttp3.OkHttpClient,
    private val serverBaseUrl: String,
    private val davUserId: String,
    private val basicAuthUser: String,
    private val password: String,
) : NextcloudDavAccess {

    private val authHeader = Credentials.basic(basicAuthUser, password)
    private val baseUrl = serverBaseUrl.trimEnd('/').toHttpUrlOrNull()
        ?: error("Invalid server base URL")
    /**
     * Authenticated DAV calls should never follow redirects.
     * A cross-host redirect could otherwise leak the `Authorization` header.
     */
    private val davHttp = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val propfindBody =
        DavPropfind.requestBodyXml.toRequestBody("application/xml; charset=utf-8".toMediaType())

    override suspend fun verifyReachable(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = filesDavUrl(davUserId, "")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .method("PROPFIND", propfindBody)
                .header("Depth", "0")
                .build()
            davHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("PROPFIND failed: HTTP ${response.code}")
                }
            }
        }
    }

    override suspend fun listChildFolders(relativeFolder: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = relativeFolder.trim().trim('/')
                val entries = propfindDepthOne(folder)
                val selfNorm = DavPropfind.normalizeHref(folderDavHref(folder))
                entries.mapNotNull { e ->
                    if (!e.isCollection) return@mapNotNull null
                    if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) return@mapNotNull null
                    hrefToRelativeUserPath(e.hrefDecoded)
                }.distinct().sortedBy { it.lowercase() }
            }
        }

    /**
     * Lists image file paths (relative to `remote.php/dav/files/{userId}/`) under [remoteFolder],
     * scanning subfolders recursively (same idea as the KDE [Nextcloud-Carousel](https://forgejo.it/Nemeyes/Nextcloud-Carousel) plugin).
     */
    /**
     * @param maxImageBytes if &gt; 0, skips files whose `getcontentlength` is known and larger than this.
     */
    override suspend fun listImageHrefsRecursive(
        remoteFolder: String,
        maxImageBytes: Long,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = remoteFolder.trim().trim('/')
            val collected = mutableListOf<String>()
            val visited = mutableSetOf<String>()
            fun visit(folder: String) {
                val key = folder.trimEnd('/') + "/"
                if (!visited.add(key)) return
                val entries = propfindDepthOne(folder)
                val selfHref = folderDavHref(folder)
                val selfNorm = DavPropfind.normalizeHref(selfHref)
                for (e in entries) {
                    if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) {
                        continue
                    }
                    when {
                        e.isCollection -> {
                            val child = hrefToRelativeUserPath(e.hrefDecoded) ?: continue
                            visit(child)
                        }
                        DavPropfind.isImageHref(e.hrefDecoded, e.contentType) -> {
                            val len = e.contentLengthBytes
                            if (maxImageBytes > 0L && len != null && len > maxImageBytes) {
                                continue
                            }
                            collected += e.hrefDecoded
                        }
                    }
                }
            }
            visit(root)
            collected.distinct()
        }
    }

    /**
     * Same as [listImageHrefsRecursive] but keeps WebDAV metadata when present (etag, lastModified, content length/type).
     * Intended for offline-first DB sync.
     */
    override suspend fun listImageEntriesRecursive(
        remoteFolder: String,
        maxImageBytes: Long,
    ): Result<List<DavEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = remoteFolder.trim().trim('/')
            val collected = mutableListOf<DavEntry>()
            val visited = mutableSetOf<String>()
            fun visit(folder: String) {
                val key = folder.trimEnd('/') + "/"
                if (!visited.add(key)) return
                val entries = propfindDepthOne(folder)
                val selfHref = folderDavHref(folder)
                val selfNorm = DavPropfind.normalizeHref(selfHref)
                for (e in entries) {
                    if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) continue
                    when {
                        e.isCollection -> {
                            val child = hrefToRelativeUserPath(e.hrefDecoded) ?: continue
                            visit(child)
                        }
                        DavPropfind.isImageHref(e.hrefDecoded, e.contentType) -> {
                            val len = e.contentLengthBytes
                            if (maxImageBytes > 0L && len != null && len > maxImageBytes) continue
                            collected += e
                        }
                    }
                }
            }
            visit(root)
            // Deduplicate by href; keep first occurrence.
            collected.distinctBy { DavPropfind.normalizeHref(it.hrefDecoded) }
        }
    }

    override suspend fun downloadFile(hrefPath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val url = absoluteUrlForHref(hrefPath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .get()
                .build()
            davHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GET failed: HTTP ${response.code}")
                response.body?.bytes() ?: error("Empty body")
            }
        }
    }

    /**
     * Downloads a file under the user’s DAV root by relative path
     * (e.g. `Photos/.ncarousel/excluded-hrefs.txt`).
     * [NoSuchElementException] when the server returns 404.
     */
    override suspend fun downloadRelativeFile(relativePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val url = filesDavUrl(davUserId, relativePath.trim().trim('/'))
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .get()
                .build()
            davHttp.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> throw NoSuchElementException("HTTP 404")
                    !response.isSuccessful -> error("GET failed: HTTP ${response.code}")
                    else -> response.body?.bytes() ?: error("Empty body")
                }
            }
        }
    }

    /**
     * Creates a collection at [relativeFolder] (MKCOL).
     * Treats “already exists” responses as success (405 / 409 / 301).
     */
    override suspend fun ensureFolder(relativeFolder: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = relativeFolder.trim().trim('/')
            if (path.isEmpty()) return@runCatching
            val url = filesDavUrl(davUserId, path)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .method("MKCOL", null)
                .build()
            davHttp.newCall(request).execute().use { response ->
                when (response.code) {
                    201, 200, 204 -> Unit
                    // Folder already present (or redirect to existing).
                    405, 409, 301, 302 -> Unit
                    else -> error("MKCOL failed: HTTP ${response.code}")
                }
            }
        }
    }

    /** Creates or overwrites a file under the user’s DAV root (PUT). */
    override suspend fun putFile(
        relativePath: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = relativePath.trim().trim('/')
            require(path.isNotEmpty()) { "Empty DAV path" }
            val url = filesDavUrl(davUserId, path)
            val body = bytes.toRequestBody(contentType.toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .put(body)
                .build()
            davHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("PUT failed: HTTP ${response.code}")
                }
            }
        }
    }

    private fun propfindDepthOne(folder: String): List<DavEntry> {
        val url = filesDavUrl(davUserId, folder)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .method("PROPFIND", propfindBody)
            .header("Depth", "1")
            .build()
        davHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val where = folder.ifBlank { "(account root)" }
                val hint404 =
                    if (response.code == 404) {
                        " Check the Remote folder value (same as in Nextcloud Files, no leading slash)."
                    } else {
                        ""
                    }
                error("PROPFIND at \"$where\": HTTP ${response.code}.$hint404")
            }
            val xml = response.body?.string() ?: error("Empty PROPFIND body")
            return DavPropfind.parse(xml)
        }
    }

    private fun folderDavHref(folder: String): String {
        val path = folder.trim().trim('/')
        val suffix = if (path.isEmpty()) "" else "$path/"
        return "/remote.php/dav/files/$davUserId/$suffix"
    }

    private fun hrefToRelativeUserPath(href: String): String? {
        val prefix = "/remote.php/dav/files/$davUserId/"
        val decoded = Uri.decode(href.trim())
        val idx = decoded.indexOf(prefix)
        if (idx < 0) return null
        return decoded.substring(idx + prefix.length).trimEnd('/')
    }

    private fun filesDavUrl(user: String, relativeFolder: String): String {
        val base = serverBaseUrl.trimEnd('/')
        val path = relativeFolder.trim().trim('/')
        val encoded = path.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                Uri.encode(segment, "@+&.=-_~:,;!$'()*")
            }
        val suffix = if (encoded.isEmpty()) "" else "/$encoded"
        return "$base/remote.php/dav/files/$user$suffix"
    }

    private fun absoluteUrlForHref(hrefPath: String): String {
        val h = hrefPath.trim()
        // Absolute URL from server: only allow same-origin to avoid leaking Authorization.
        val abs = h.toHttpUrlOrNull()
        if (abs != null) {
            val sameOrigin =
                abs.scheme == baseUrl.scheme &&
                    abs.host == baseUrl.host &&
                    abs.port == baseUrl.port
            if (!sameOrigin) error("Refusing cross-origin DAV href")
            return abs.toString()
        }

        // Relative href: resolve against configured base.
        val rel = if (h.startsWith("/")) h else "/$h"
        return baseUrl.newBuilder()
            .encodedPath(rel)
            .build()
            .toString()
    }
}
