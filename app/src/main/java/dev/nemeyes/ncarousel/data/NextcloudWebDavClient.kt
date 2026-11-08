package dev.nemeyes.ncarousel.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.coroutineContext

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
            val url = filesDavUrl(davUserId, "", collection = true)
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
                val entries = propfindUrl(
                    filesDavUrl(davUserId, folder, collection = true),
                    depth = "1",
                    missingOk = false,
                    whereLabel = folder,
                )
                val selfNorm = DavPropfind.normalizeHref(folderDavHref(folder))
                entries.mapNotNull { e ->
                    if (!e.isCollection) return@mapNotNull null
                    if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) return@mapNotNull null
                    DavPropfind.hrefToRelativeUserPath(e.hrefDecoded, davUserId, parentFolder = folder)
                }.distinct().sortedBy { it.lowercase() }
            }
        }

    /**
     * Lists image file paths under [remoteFolder] via recursive `Depth: 1` PROPFIND
     * (Nextcloud documents Depth 0/1 for folder listings; RFC 4918 §9.1 allows servers to
     * reject or mishandle `Depth: infinity` — do not use infinity here).
     *
     * @param maxImageBytes if &gt; 0, skips files whose `getcontentlength` is known and larger than this.
     */
    override suspend fun listImageHrefsRecursive(
        remoteFolder: String,
        maxImageBytes: Long,
    ): Result<List<String>> =
        listImageEntriesRecursive(remoteFolder, maxImageBytes).map { list -> list.map { it.hrefDecoded } }

    /**
     * Same as [listImageHrefsRecursive] but keeps WebDAV metadata when present (etag, lastModified, content length/type).
     * Intended for offline-first DB sync.
     */
    override suspend fun listImageEntriesRecursive(
        remoteFolder: String,
        maxImageBytes: Long,
    ): Result<List<DavEntry>> = withContext(Dispatchers.IO) {
        try {
            val root = remoteFolder.trim().trim('/')
            Result.success(listImagesDepthOneWalk(root, maxImageBytes))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Depth:1 walk aligned with v0.2.55 visit semantics, with one count-critical fix:
     * nested collections are PROPFIND'd at the **exact href** from the parent Multistatus
     * (RFC 4918 §8.3), not at a re-joined relative path. Wrong joins + nested `missingOk`
     * previously skipped reachable subtrees (~760 images vs v0.2.55).
     *
     * Collection URLs use a trailing slash (RFC 4918 §5.2 / Nextcloud Depth 0|1 listings).
     * Walk is strictly serial (no parallel PROPFIND); [DavPropfind.INTER_PROPFIND_DELAY_MS]
     * paces nested visits; [ensureActive] stops the walk when the refresh job is cancelled
     * so a new refresh cannot overlap an abandoned tree walk.
     */
    private suspend fun listImagesDepthOneWalk(root: String, maxImageBytes: Long): List<DavEntry> {
        val collected = mutableListOf<DavEntry>()
        val visited = mutableSetOf<String>()
        suspend fun visitUrl(requestUrl: String, isRoot: Boolean, whereLabel: String) {
            coroutineContext.ensureActive()
            val selfNorm = DavPropfind.normalizeHref(requestUrl)
            if (!visited.add(selfNorm)) return
            if (!isRoot && DavPropfind.INTER_PROPFIND_DELAY_MS > 0L) {
                delay(DavPropfind.INTER_PROPFIND_DELAY_MS)
            }
            // Nested 404 (gone mount) must not abort the whole library; exact-href visits
            // avoid false 404s from bad path joins that missingOk used to swallow.
            val entries = propfindUrl(
                requestUrl,
                depth = "1",
                missingOk = !isRoot,
                whereLabel = whereLabel,
            )
            for (e in entries) {
                if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) continue
                when {
                    e.isCollection -> {
                        val childUrl = collectionUrlFromHref(e.hrefDecoded)
                        val childLabel =
                            DavPropfind.hrefToRelativeUserPath(e.hrefDecoded, davUserId).orEmpty()
                                .ifBlank { e.hrefDecoded }
                        visitUrl(childUrl, isRoot = false, whereLabel = childLabel)
                    }
                    DavPropfind.isImageHref(e.hrefDecoded, e.contentType) -> {
                        val len = e.contentLengthBytes
                        if (maxImageBytes > 0L && len != null && len > maxImageBytes) continue
                        collected += e
                    }
                }
            }
        }
        val rootUrl = filesDavUrl(davUserId, root, collection = true)
        visitUrl(rootUrl, isRoot = true, whereLabel = root.ifBlank { "(account root)" })
        return collected.distinctBy { DavPropfind.normalizeHref(it.hrefDecoded) }
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
            val url = filesDavUrl(davUserId, relativePath.trim().trim('/'), collection = false)
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
            // MKCOL: keep non-slash URL form (same as v0.2.55); slash is for PROPFIND only.
            val url = filesDavUrl(davUserId, path, collection = false)
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
            val url = filesDavUrl(davUserId, path, collection = false)
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

    private fun propfindUrl(
        url: String,
        depth: String,
        missingOk: Boolean,
        whereLabel: String,
    ): List<DavEntry> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .method("PROPFIND", propfindBody)
            .header("Depth", depth)
            .build()
        davHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (missingOk && response.code == 404) return emptyList()
                val where = whereLabel.ifBlank { "(account root)" }
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

    /**
     * @param collection when true, append a trailing `/` (RFC 4918 §5.2 clients SHOULD use
     * the slash form for collections; avoids relative-href resolution against the parent segment).
     */
    private fun filesDavUrl(user: String, relativeFolder: String, collection: Boolean): String {
        val base = serverBaseUrl.trimEnd('/')
        val path = relativeFolder.trim().trim('/')
        val encoded = path.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                Uri.encode(segment, "@+&.=-_~:,;!$'()*")
            }
        val slash = if (collection) "/" else ""
        val suffix = if (encoded.isEmpty()) slash else "/$encoded$slash"
        return "$base/remote.php/dav/files/$user$suffix"
    }

    /**
     * PROPFIND URL for a collection href from Multistatus.
     * Prefers the server’s `/remote.php/dav/files/{user}/…` path (keeps the href’s user
     * segment) with per-segment encoding + trailing slash — avoids both wrong parent joins
     * and broken `toHttpUrlOrNull` on decoded absolute URLs that contain spaces.
     */
    private fun collectionUrlFromHref(hrefDecoded: String): String {
        val decoded = hrefDecoded.trim()
        val marker = "/remote.php/dav/files/"
        val idx = decoded.indexOf(marker, ignoreCase = true)
        if (idx >= 0) {
            val fromMarker = decoded.substring(idx).trimEnd('/')
            val encodedPath = fromMarker.split('/').joinToString("/") { seg ->
                if (seg.isEmpty()) "" else Uri.encode(seg, "@+&.=-_~:,;!$'()*")
            } + "/"
            return baseUrl.newBuilder()
                .encodedPath(encodedPath)
                .build()
                .toString()
        }
        val rel = DavPropfind.hrefToRelativeUserPath(decoded, davUserId)
            ?: error("Cannot resolve collection href for PROPFIND: $decoded")
        return filesDavUrl(davUserId, rel, collection = true)
    }

    private fun absoluteUrlForHref(hrefPath: String): String {
        val h = hrefPath.trim()
        if (h.startsWith("http://", ignoreCase = true) || h.startsWith("https://", ignoreCase = true)) {
            // Decoded absolute href may contain spaces; rebuild from scheme/host + encoded path.
            val uri = Uri.parse(h)
            val host = uri.host ?: error("Invalid DAV href host")
            if (!host.equals(baseUrl.host, ignoreCase = true)) {
                error("Refusing cross-origin DAV href")
            }
            val scheme = uri.scheme ?: baseUrl.scheme
            val port = if (uri.port != -1) uri.port else baseUrl.port
            val pathDecoded = uri.path.orEmpty().ifBlank { error("Invalid DAV href path") }
            val encodedPath = pathDecoded.split('/').joinToString("/") { seg ->
                if (seg.isEmpty()) "" else Uri.encode(seg, "@+&.=-_~:,;!$'()*")
            }
            val portPart =
                if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) {
                    ""
                } else {
                    ":$port"
                }
            return "$scheme://$host$portPart$encodedPath"
        }

        // Path-absolute (decoded): encode each segment for OkHttp encodedPath.
        val pathDecoded = if (h.startsWith("/")) h else "/$h"
        val encodedPath = pathDecoded.split('/').joinToString("/") { seg ->
            if (seg.isEmpty()) "" else Uri.encode(seg, "@+&.=-_~:,;!$'()*")
        }
        return baseUrl.newBuilder()
            .encodedPath(encodedPath)
            .build()
            .toString()
    }
}
