package dev.nemeyes.ncarousel.data

import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Shared WebDAV PROPFIND body + Multistatus XML parsing for OkHttp and SSO clients. */
object DavPropfind {

    /**
     * Pause between nested Depth:1 PROPFINDs during a recursive library walk.
     * Listing stays single-threaded; this only softens sustained PHP-FPM/DB load on
     * large folder trees (thousands of collections). Does not change which folders are visited.
     */
    const val INTER_PROPFIND_DELAY_MS: Long = 40L

    val requestBodyXml: String = """
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
    """.trimIndent()

    fun parse(xml: String): List<DavEntry> {
        val trimmed = xml.trim()
        if (trimmed.isEmpty()) error("Empty PROPFIND body")
        // HTML / JSON error pages must not look like an empty folder.
        if (!trimmed.contains("multistatus", ignoreCase = true) &&
            !trimmed.contains(":response", ignoreCase = true) &&
            !trimmed.contains("<response", ignoreCase = true)
        ) {
            val preview = trimmed.take(120).replace('\n', ' ')
            error("Unexpected PROPFIND response (not WebDAV Multistatus): $preview")
        }
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(trimmed))
        val list = mutableListOf<DavEntry>()
        var currentHref: String? = null
        var inResourceType = false
        var hasCollection = false
        var contentType: String? = null
        var contentLengthBytes: Long? = null
        var etag: String? = null
        var lastModified: String? = null
        var fileId: Long? = null

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
                            fileId = null
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
                            contentLengthBytes = readTextElementContents(parser).toLongOrNull()
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
                    if (parser.name.equals("response", ignoreCase = true)) flushResponse()
                    if (parser.name.equals("resourcetype", ignoreCase = true)) inResourceType = false
                }
            }
            event = parser.next()
        }
        return list
    }

    fun isImageHref(href: String, contentType: String?): Boolean {
        val ct = contentType?.lowercase() ?: ""
        if (ct.startsWith("image/") && !ct.contains("svg")) return true
        val lower = href.lowercase()
        return IMAGE_EXTENSIONS.any { lower.endsWith(it) }
    }

    /**
     * Path-only identity for self/child comparisons (absolute `https://…` vs `/remote.php/…`).
     * Strips down to `/remote.php/dav/files/…` when present so host-absolute and root-absolute
     * forms of the same resource compare equal.
     */
    fun normalizeHref(href: String): String {
        val decoded = Uri.decode(href.trim())
        val marker = "/remote.php/dav/files/"
        val idx = decoded.indexOf(marker, ignoreCase = true)
        val path = if (idx >= 0) decoded.substring(idx) else decoded
        return path.trimEnd('/').lowercase()
    }

    /**
     * Turns a DAV href (absolute, root-absolute, or relative) into a path relative to
     * `/remote.php/dav/files/{user}/` (the `{user}` segment is stripped, not kept).
     *
     * [davUserId] is accepted for API compatibility; the first path segment after `files/` is
     * always stripped so SSO/login-name vs UID mismatches do not drop whole subtrees.
     *
     * RFC 4918 §8.3 relative refs in Multistatus are **path-absolute** (start with `/`) or
     * absolute-URI. Path-absolute hrefs that are not under `/remote.php/dav/files/` return
     * null — never invent a `parentFolder` join (that produced PROPFIND 404s which `missingOk`
     * then swallowed, undercounting vs v0.2.55). [parentFolder] is only for rare non-standard
     * segment-relative hrefs (e.g. `2024/`).
     */
    @Suppress("UNUSED_PARAMETER")
    fun hrefToRelativeUserPath(href: String, davUserId: String, parentFolder: String = ""): String? {
        val decoded = Uri.decode(href.trim())
        val marker = "/remote.php/dav/files/"
        val idx = decoded.indexOf(marker, ignoreCase = true)
        if (idx >= 0) {
            val after = decoded.substring(idx + marker.length)
            val slash = after.indexOf('/')
            // Strip whatever user segment the server put in the href (uid, login, …).
            return if (slash < 0) "" else after.substring(slash + 1).trimEnd('/')
        }
        if (decoded.contains("://")) return null
        // path-absolute but not a DAV files href — do not parent-join (404 bait).
        if (decoded.startsWith("/")) return null
        val rel = decoded.trim().trimEnd('/')
        if (rel.isEmpty() || rel.startsWith("remote.php", ignoreCase = true)) return null
        val parent = parentFolder.trim().trim('/')
        if (parent.isEmpty()) return rel
        if (rel.equals(parent, ignoreCase = true)) return parent
        if (rel.startsWith("$parent/", ignoreCase = true)) return rel
        return "$parent/$rel"
    }

    private fun readTextElementContents(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var type = parser.next()
        while (type != XmlPullParser.END_TAG) {
            if (type == XmlPullParser.TEXT) sb.append(parser.text)
            type = parser.next()
        }
        return sb.toString().trim()
    }

    private val IMAGE_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif",
    )
}
