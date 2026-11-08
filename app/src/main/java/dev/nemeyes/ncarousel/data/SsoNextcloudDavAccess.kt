package dev.nemeyes.ncarousel.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.coroutines.coroutineContext

/**
 * WebDAV / file access via Nextcloud Files Single Sign-On ([NextcloudAPI]).
 * Must be used when [dev.nemeyes.ncarousel.data.accounts.NextcloudAccount.usesSso] is true.
 *
 * [NextcloudAPI] is reused per SSO account (official guidance: keep the binder open across
 * calls). Access is synchronized per account so listing and previews share one connection
 * without open/close churn on every thumb.
 */
class SsoNextcloudDavAccess(
    context: Context,
    private val ssoAccountName: String,
    private val davUserId: String,
) : NextcloudDavAccess {

    private val app = context.applicationContext

    companion object {
        private val gson = Gson()
        private val apiLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        private val apis = java.util.concurrent.ConcurrentHashMap<String, NextcloudAPI>()

        private fun lockFor(accountName: String): Any =
            apiLocks.getOrPut(accountName) { Any() }

        /** Drop a cached API (e.g. after account removal). Safe if never opened. */
        fun closeCached(ssoAccountName: String) {
            val name = ssoAccountName.trim()
            if (name.isEmpty()) return
            synchronized(lockFor(name)) {
                apis.remove(name)?.close()
            }
        }
    }

    override suspend fun verifyReachable(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requestBytes(
                "PROPFIND",
                filesDavPath("", collection = true),
                body = DavPropfind.requestBodyXml,
                headers = mapOf(
                    "Depth" to listOf("0"),
                    "Content-Type" to listOf("application/xml; charset=utf-8"),
                ),
            )
            Unit
        }
    }

    override suspend fun listChildFolders(relativeFolder: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = relativeFolder.trim().trim('/')
                val entries = propfindPath(
                    filesDavPath(folder, collection = true),
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

    override suspend fun listImageHrefsRecursive(
        remoteFolder: String,
        maxImageBytes: Long,
    ): Result<List<String>> =
        listImageEntriesRecursive(remoteFolder, maxImageBytes).map { list -> list.map { it.hrefDecoded } }

    override suspend fun listImageEntriesRecursive(
        remoteFolder: String,
        maxImageBytes: Long,
    ): Result<List<DavEntry>> = withContext(Dispatchers.IO) {
        try {
            val root = remoteFolder.trim().trim('/')
            // Recursive Depth:1 only — do not use Depth:infinity (RFC 4918 §9.1 / Nextcloud may
            // return an unusable Multistatus that previously emptied the library).
            Result.success(listImagesDepthOneWalk(root, maxImageBytes))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Same Depth:1 walk as [NextcloudWebDavClient]: PROPFIND nested collections at the exact
     * Multistatus href (not a re-joined relative path). Serial + paced; see OkHttp twin.
     */
    private suspend fun listImagesDepthOneWalk(root: String, maxImageBytes: Long): List<DavEntry> {
        val collected = mutableListOf<DavEntry>()
        val visited = mutableSetOf<String>()
        suspend fun visitPath(requestPath: String, isRoot: Boolean, whereLabel: String) {
            coroutineContext.ensureActive()
            val selfNorm = DavPropfind.normalizeHref(requestPath)
            if (!visited.add(selfNorm)) return
            if (!isRoot && DavPropfind.INTER_PROPFIND_DELAY_MS > 0L) {
                delay(DavPropfind.INTER_PROPFIND_DELAY_MS)
            }
            val entries = propfindPath(
                requestPath,
                depth = "1",
                missingOk = !isRoot,
                whereLabel = whereLabel,
            )
            for (e in entries) {
                if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) continue
                when {
                    e.isCollection -> {
                        val childPath = collectionPathFromHref(e.hrefDecoded)
                        val childLabel =
                            DavPropfind.hrefToRelativeUserPath(e.hrefDecoded, davUserId).orEmpty()
                                .ifBlank { e.hrefDecoded }
                        visitPath(childPath, isRoot = false, whereLabel = childLabel)
                    }
                    DavPropfind.isImageHref(e.hrefDecoded, e.contentType) -> {
                        val len = e.contentLengthBytes
                        if (maxImageBytes > 0L && len != null && len > maxImageBytes) continue
                        collected += e
                    }
                }
            }
        }
        val rootPath = filesDavPath(root, collection = true)
        visitPath(rootPath, isRoot = true, whereLabel = root.ifBlank { "(account root)" })
        return collected.distinctBy { DavPropfind.normalizeHref(it.hrefDecoded) }
    }

    override suspend fun downloadFile(hrefPath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val path = davPathFromHref(hrefPath)
            requestBytes("GET", path)
        }
    }

    override suspend fun downloadRelativeFile(relativePath: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = filesDavPath(relativePath.trim().trim('/'), collection = false)
                try {
                    requestBytes("GET", path)
                } catch (e: NextcloudHttpRequestFailedException) {
                    if (e.statusCode == 404) throw NoSuchElementException("HTTP 404")
                    throw e
                }
            }
        }

    override suspend fun ensureFolder(relativeFolder: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = relativeFolder.trim().trim('/')
            if (path.isEmpty()) return@runCatching
            try {
                // MKCOL: keep non-slash URL form (same as OkHttp / v0.2.55).
                requestBytes("MKCOL", filesDavPath(path, collection = false), body = null)
            } catch (e: NextcloudHttpRequestFailedException) {
                when (e.statusCode) {
                    201, 200, 204, 405, 409, 301, 302 -> Unit
                    else -> throw e
                }
            }
        }
    }

    override suspend fun putFile(
        relativePath: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val path = relativePath.trim().trim('/')
            require(path.isNotEmpty()) { "Empty DAV path" }
            requestBytes(
                method = "PUT",
                urlPath = filesDavPath(path, collection = false),
                bodyBytes = bytes,
                contentType = contentType,
            )
            Unit
        }
    }

    /**
     * Downloads a Nextcloud core preview image for [fileId] (Library thumbs / fullscreen).
     *
     * Takes the per-account API lock **before** [LibraryPreviewGate] so waiting SSO callers
     * do not hold preview permits (Basic Auth Coil can still progress). Reuses one
     * [NextcloudAPI] — do not open/close the binder per thumb.
     */
    suspend fun downloadPreview(fileId: Long, sizePx: Int): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Official pattern: GET /index.php/core/preview?fileId=&x=&y= (WebDAV
                // property nc:has-preview indicates availability). Generation is CPU/RAM
                // heavy; Nextcloud documents preview_concurrency_* on the server side.
                val path = "/index.php/core/preview?fileId=$fileId&x=$sizePx&y=$sizePx&a=1"
                val lock = lockFor(ssoAccountName)
                synchronized(lock) {
                    LibraryPreviewGate.withPermitBlocking {
                        requestBytesLocked("GET", path)
                    }
                }
            }
        }

    private fun propfindPath(
        path: String,
        depth: String,
        missingOk: Boolean,
        whereLabel: String,
    ): List<DavEntry> {
        val headers = mapOf(
            "Depth" to listOf(depth),
            "Content-Type" to listOf("application/xml; charset=utf-8"),
        )
        val xml = try {
            requestString("PROPFIND", path, body = DavPropfind.requestBodyXml, headers = headers)
        } catch (e: NextcloudHttpRequestFailedException) {
            if (missingOk && e.statusCode == 404) return emptyList()
            val where = whereLabel.ifBlank { "(account root)" }
            val hint404 =
                if (e.statusCode == 404) {
                    " Check the Remote folder value (same as in Nextcloud Files, no leading slash)."
                } else {
                    ""
                }
            error("PROPFIND at \"$where\": HTTP ${e.statusCode}.$hint404")
        }
        return DavPropfind.parse(xml)
    }

    private fun requestString(
        method: String,
        urlPath: String,
        body: String? = null,
        headers: Map<String, List<String>> = emptyMap(),
    ): String = String(requestBytes(method, urlPath, body = body, headers = headers), Charsets.UTF_8)

    private fun requestBytes(
        method: String,
        urlPath: String,
        body: String? = null,
        bodyBytes: ByteArray? = null,
        contentType: String? = null,
        headers: Map<String, List<String>> = emptyMap(),
    ): ByteArray {
        val lock = lockFor(ssoAccountName)
        synchronized(lock) {
            return requestBytesLocked(method, urlPath, body, bodyBytes, contentType, headers)
        }
    }

    /**
     * Caller must already hold [lockFor] for [ssoAccountName] (re-entrant from
     * [downloadPreview] which acquires the gate under the same lock).
     */
    private fun requestBytesLocked(
        method: String,
        urlPath: String,
        body: String? = null,
        bodyBytes: ByteArray? = null,
        contentType: String? = null,
        headers: Map<String, List<String>> = emptyMap(),
    ): ByteArray {
        val api = apis.getOrPut(ssoAccountName) {
            val account = AccountImporter.getSingleSignOnAccount(app, ssoAccountName)
            // Keep the binder open across calls (Android-SingleSignOn NextcloudAPI javadoc).
            NextcloudAPI(app, account, gson)
        }
        val builder = NextcloudRequest.Builder()
            .setMethod(method)
            // [urlPath] must be a *decoded* path-absolute URL. Encode exactly once here.
            // Pre-encoding segments and encoding again turned `%20` into `%2520`, so PROPFIND
            // 404'd folders with spaces/unicode; nested missingOk then dropped those subtrees
            // (undercount vs OkHttp / v0.2.55).
            .setUrl(Uri.encode(urlPath, "/@&=+$,:;!?'()*"))
        val hdrs = HashMap(headers)
        if (contentType != null) {
            hdrs["Content-Type"] = listOf(contentType)
        }
        if (hdrs.isNotEmpty()) {
            builder.setHeader(hdrs)
        }
        when {
            bodyBytes != null -> builder.setRequestBodyAsStream(ByteArrayInputStream(bodyBytes))
            body != null -> builder.setRequestBody(body)
        }
        return try {
            val response = api.performNetworkRequestV2(builder.build())
            response.body.use { it.readBytes() }
        } catch (e: NextcloudHttpRequestFailedException) {
            throw e
        } catch (e: Exception) {
            // Stale binder / Files app restart: drop cache so the next call reconnects.
            // Do not invalidate on ordinary HTTP failures (404, 403, …).
            apis.remove(ssoAccountName)?.close()
            throw e
        }
    }

    private fun folderDavHref(folder: String): String {
        val path = folder.trim().trim('/')
        val suffix = if (path.isEmpty()) "" else "$path/"
        return "/remote.php/dav/files/$davUserId/$suffix"
    }

    /** Decoded DAV path; [requestBytes] applies a single [Uri.encode]. */
    private fun filesDavPath(relativeFolder: String, collection: Boolean): String {
        val path = relativeFolder.trim().trim('/')
        val slash = if (collection) "/" else ""
        val suffix = if (path.isEmpty()) slash else "/$path$slash"
        return "/remote.php/dav/files/$davUserId$suffix"
    }

    /** Collection PROPFIND path from a Multistatus href (trailing slash, decoded). */
    private fun collectionPathFromHref(hrefDecoded: String): String =
        davPathFromHref(hrefDecoded).trimEnd('/') + "/"

    /** Decoded path-absolute for SSO [requestBytes] (single encode at the call site). */
    private fun davPathFromHref(hrefPath: String): String {
        val h = hrefPath.trim()
        if (h.startsWith("http://", ignoreCase = true) || h.startsWith("https://", ignoreCase = true)) {
            val uri = Uri.parse(h)
            val decoded = uri.path.orEmpty().ifBlank { error("Invalid DAV href") }
            return decoded
        }
        return if (h.startsWith("/")) h else "/$h"
    }
}
