package dev.nemeyes.ncarousel.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

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
                filesDavPath(""),
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
                val entries = propfind(folder, depth = "1", missingOk = false)
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
        runCatching {
            val root = remoteFolder.trim().trim('/')
            // Recursive Depth:1 only — do not use Depth:infinity (RFC 4918 §9.1 / Nextcloud may
            // return an unusable Multistatus that previously emptied the library).
            listImagesDepthOneWalk(root, maxImageBytes)
        }
    }

    /**
     * Same proven Depth:1 walk as [NextcloudWebDavClient] (v0.2.55 / `b52b76e` semantics).
     */
    private fun listImagesDepthOneWalk(root: String, maxImageBytes: Long): List<DavEntry> {
        val collected = mutableListOf<DavEntry>()
        val visited = mutableSetOf<String>()
        fun visit(folder: String, isRoot: Boolean) {
            val key = folder.trimEnd('/').lowercase() + "/"
            if (!visited.add(key)) return
            val entries = propfind(folder, depth = "1", missingOk = !isRoot)
            val selfNorm = DavPropfind.normalizeHref(folderDavHref(folder))
            for (e in entries) {
                if (DavPropfind.normalizeHref(e.hrefDecoded) == selfNorm) continue
                when {
                    e.isCollection -> {
                        val child = DavPropfind.hrefToRelativeUserPath(
                            e.hrefDecoded,
                            davUserId,
                            parentFolder = folder,
                        ) ?: continue
                        visit(child, isRoot = false)
                    }
                    DavPropfind.isImageHref(e.hrefDecoded, e.contentType) -> {
                        val len = e.contentLengthBytes
                        if (maxImageBytes > 0L && len != null && len > maxImageBytes) continue
                        collected += e
                    }
                }
            }
        }
        visit(root, isRoot = true)
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
                val path = filesDavPath(relativePath.trim().trim('/'))
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
                requestBytes("MKCOL", filesDavPath(path), body = null)
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
                urlPath = filesDavPath(path),
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

    private fun propfind(folder: String, depth: String, missingOk: Boolean): List<DavEntry> {
        val path = filesDavPath(folder)
        val headers = mapOf(
            "Depth" to listOf(depth),
            "Content-Type" to listOf("application/xml; charset=utf-8"),
        )
        val xml = try {
            requestString("PROPFIND", path, body = DavPropfind.requestBodyXml, headers = headers)
        } catch (e: NextcloudHttpRequestFailedException) {
            if (missingOk && e.statusCode == 404) return emptyList()
            val where = folder.ifBlank { "(account root)" }
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
                // Match the path encoding that successfully listed ~4499 images on `b52b76e`
                // (pre-encode segments + encode once for NextcloudAPI). Raw setUrl after e4b9228
                // correlated with empty/failed refresh for some accounts.
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

    private fun filesDavPath(relativeFolder: String): String {
        val path = relativeFolder.trim().trim('/')
        val encoded = path.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                Uri.encode(segment, "@+&.=-_~:,;!$'()*")
            }
        val suffix = if (encoded.isEmpty()) "" else "/$encoded"
        return "/remote.php/dav/files/$davUserId$suffix"
    }

    private fun davPathFromHref(hrefPath: String): String {
        val h = hrefPath.trim()
        if (h.startsWith("http://", ignoreCase = true) || h.startsWith("https://", ignoreCase = true)) {
            val uri = Uri.parse(h)
            return uri.encodedPath.orEmpty().ifBlank { error("Invalid DAV href") }
        }
        return if (h.startsWith("/")) h else "/$h"
    }
}
