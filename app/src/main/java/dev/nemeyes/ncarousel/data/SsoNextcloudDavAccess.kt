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
                DavPropfind.childCollectionPaths(entries, davUserId, folder)
                    .sortedBy { it.lowercase() }
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
            listImagesPreferInfinity(root, maxImageBytes)
        }
    }

    private fun listImagesPreferInfinity(root: String, maxImageBytes: Long): List<DavEntry> {
        val infinity = runCatching { propfind(root, depth = "infinity", missingOk = false) }.getOrNull()
        if (infinity != null) {
            return DavPropfind.collectImagesFromEntries(
                entries = infinity,
                davUserId = davUserId,
                rootFolder = root,
                parentFolder = root,
                maxImageBytes = maxImageBytes,
            )
        }
        return listImagesDepthOneWalk(root, maxImageBytes)
    }

    private fun listImagesDepthOneWalk(root: String, maxImageBytes: Long): List<DavEntry> {
        val collected = LinkedHashMap<String, DavEntry>()
        val visited = mutableSetOf<String>()
        fun visit(folder: String, isRoot: Boolean) {
            val key = folder.trimEnd('/').lowercase() + "/"
            if (!visited.add(key)) return
            val entries = propfind(folder, depth = "1", missingOk = !isRoot)
            for (img in DavPropfind.collectImagesFromEntries(
                entries = entries,
                davUserId = davUserId,
                rootFolder = root,
                parentFolder = folder,
                maxImageBytes = maxImageBytes,
            )) {
                val rel = DavPropfind.hrefToRelativeUserPath(img.hrefDecoded, davUserId) ?: continue
                collected.putIfAbsent(DavPropfind.imageIdentityKey(img, rel), img)
            }
            for (child in DavPropfind.childCollectionPaths(entries, davUserId, folder)) {
                visit(child, isRoot = false)
            }
        }
        visit(root, isRoot = true)
        return collected.values.toList()
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
            if (depth.equals("infinity", ignoreCase = true)) {
                error("PROPFIND Depth:infinity unsupported: HTTP ${e.statusCode}")
            }
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
                // NextcloudAPI encodes the path; pass a raw path (no pre-encode) to avoid %20 → %2520.
                .setUrl(urlPath)
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

    /** Raw (decoded) DAV path; SSO [NextcloudAPI] applies encoding once. */
    private fun filesDavPath(relativeFolder: String): String {
        val path = relativeFolder.trim().trim('/')
        val suffix = if (path.isEmpty()) "" else "/$path"
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
