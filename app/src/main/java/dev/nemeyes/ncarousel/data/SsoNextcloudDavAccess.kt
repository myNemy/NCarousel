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
 */
class SsoNextcloudDavAccess(
    context: Context,
    private val ssoAccountName: String,
    private val davUserId: String,
) : NextcloudDavAccess {

    private val app = context.applicationContext

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

    /** Downloads a Nextcloud core preview image for [fileId] (Library thumbnails). */
    suspend fun downloadPreview(fileId: Long, sizePx: Int): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/index.php/core/preview?fileId=$fileId&x=$sizePx&y=$sizePx&a=1"
            requestBytes("GET", path)
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
        val account = AccountImporter.getSingleSignOnAccount(app, ssoAccountName)
        NextcloudAPI(app, account, Gson()).use { api ->
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
            val response = api.performNetworkRequestV2(builder.build())
            return response.body.use { it.readBytes() }
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
