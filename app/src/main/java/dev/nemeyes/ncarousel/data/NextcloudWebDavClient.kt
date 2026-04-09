package dev.nemeyes.ncarousel.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

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
) {

    private val authHeader = Credentials.basic(basicAuthUser, password)

    private val propfindBody = """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
          <d:prop>
            <d:getlastmodified/>
            <d:getcontentlength/>
            <d:getcontenttype/>
            <oc:fileid/>
            <oc:permissions/>
            <d:resourcetype/>
            <d:getetag/>
          </d:prop>
        </d:propfind>
    """.trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

    suspend fun verifyReachable(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = filesDavUrl(davUserId, "")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .method("PROPFIND", propfindBody)
                .header("Depth", "0")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("PROPFIND failed: HTTP ${response.code}")
                }
            }
        }
    }

    /**
     * Lists image file paths (relative to `remote.php/dav/files/{userId}/`) under [remoteFolder],
     * scanning subfolders recursively (same idea as the KDE [Nextcloud-Carousel](https://forgejo.it/Nemeyes/Nextcloud-Carousel) plugin).
     */
    /**
     * @param maxImageBytes if &gt; 0, skips files whose `getcontentlength` is known and larger than this.
     */
    suspend fun listImageHrefsRecursive(
        remoteFolder: String,
        maxImageBytes: Long = 0L,
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
                val selfNorm = normalizeHref(selfHref)
                for (e in entries) {
                    if (normalizeHref(e.hrefDecoded) == selfNorm) {
                        continue
                    }
                    when {
                        e.isCollection -> {
                            val child = hrefToRelativeUserPath(e.hrefDecoded) ?: continue
                            visit(child)
                        }
                        isImageHref(e.hrefDecoded, e.contentType) -> {
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
    suspend fun listImageEntriesRecursive(
        remoteFolder: String,
        maxImageBytes: Long = 0L,
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
                val selfNorm = normalizeHref(selfHref)
                for (e in entries) {
                    if (normalizeHref(e.hrefDecoded) == selfNorm) continue
                    when {
                        e.isCollection -> {
                            val child = hrefToRelativeUserPath(e.hrefDecoded) ?: continue
                            visit(child)
                        }
                        isImageHref(e.hrefDecoded, e.contentType) -> {
                            val len = e.contentLengthBytes
                            if (maxImageBytes > 0L && len != null && len > maxImageBytes) continue
                            collected += e
                        }
                    }
                }
            }
            visit(root)
            // Deduplicate by href; keep first occurrence.
            collected.distinctBy { normalizeHref(it.hrefDecoded) }
        }
    }

    suspend fun downloadFile(hrefPath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val url = absoluteUrlForHref(hrefPath)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GET failed: HTTP ${response.code}")
                response.body?.bytes() ?: error("Empty body")
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
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val where = folder.ifBlank { "(radice account)" }
                val hint404 =
                    if (response.code == 404) {
                        " Controlla il campo «Cartella remota» nell’app (deve essere come in Nextcloud Files, senza slash iniziale)."
                    } else {
                        ""
                    }
                error("PROPFIND su «$where»: HTTP ${response.code}.$hint404")
            }
            val xml = response.body?.string() ?: error("Empty PROPFIND body")
            return parsePropfind(xml)
        }
    }

    private fun normalizeHref(href: String): String =
        Uri.decode(href.trim()).trimEnd('/').lowercase()

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
        if (h.startsWith("http://", ignoreCase = true) || h.startsWith("https://", ignoreCase = true)) {
            return h
        }
        return serverBaseUrl.trimEnd('/') + (if (h.startsWith("/")) h else "/$h")
    }

    private fun parsePropfind(xml: String): List<DavEntry> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val list = mutableListOf<DavEntry>()
        var currentHref: String? = null
        var inResourceType = false
        var hasCollection = false
        var contentType: String? = null
        var contentLengthBytes: Long? = null
        var etag: String? = null
        var lastModified: String? = null
        var fileId: Long? = null
        var depth = 0

        fun flushResponse() {
            val href = currentHref ?: return
            list += DavEntry(
                hrefDecoded = Uri.decode(href),
                isCollection = hasCollection,
                fileId = fileId,
                contentType = contentType,
                contentLengthBytes = contentLengthBytes,
                etag = etag,
                lastModified = lastModified,
            )
            currentHref = null
            inResourceType = false
            hasCollection = false
            contentType = null
            contentLengthBytes = null
            etag = null
            lastModified = null
            fileId = null
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val name = parser.name
                    when {
                        name.equals("response", ignoreCase = true) -> {
                            currentHref = null
                            inResourceType = false
                            hasCollection = false
                            contentType = null
                            contentLengthBytes = null
                            etag = null
                            lastModified = null
                        }
                        name.equals("href", ignoreCase = true) -> {
                            currentHref = readTextElementContents(parser)
                        }
                        name.equals("resourcetype", ignoreCase = true) -> inResourceType = true
                        inResourceType && name.equals("collection", ignoreCase = true) -> hasCollection = true
                        name.equals("getcontenttype", ignoreCase = true) -> {
                            contentType = readTextElementContents(parser).ifBlank { null }
                        }
                        name.equals("getcontentlength", ignoreCase = true) -> {
                            val raw = readTextElementContents(parser)
                            contentLengthBytes = raw.toLongOrNull()
                        }
                        name.equals("getetag", ignoreCase = true) -> {
                            etag = readTextElementContents(parser).ifBlank { null }
                        }
                        name.equals("getlastmodified", ignoreCase = true) -> {
                            lastModified = readTextElementContents(parser).ifBlank { null }
                        }
                        name.equals("fileid", ignoreCase = true) -> {
                            fileId = readTextElementContents(parser).toLongOrNull()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("response", ignoreCase = true)) {
                        flushResponse()
                    }
                    if (parser.name.equals("resourcetype", ignoreCase = true)) {
                        inResourceType = false
                    }
                    depth--
                }
            }
            event = parser.next()
        }
        return list
    }

    /** Reads character data until the matching end tag (caller is on START_TAG). */
    private fun readTextElementContents(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var type = parser.next()
        while (type != XmlPullParser.END_TAG) {
            if (type == XmlPullParser.TEXT) sb.append(parser.text)
            type = parser.next()
        }
        return sb.toString().trim()
    }

    private fun isImageHref(href: String, contentType: String?): Boolean {
        val ct = contentType?.lowercase() ?: ""
        if (ct.startsWith("image/") && !ct.contains("svg")) return true
        val lower = href.lowercase()
        return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    companion object {
        private val IMAGE_EXTENSIONS = listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif",
        )
    }
}
