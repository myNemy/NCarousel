package dev.nemeyes.ncarousel.data

import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** Shared WebDAV PROPFIND body + Multistatus XML parsing for OkHttp and SSO clients. */
object DavPropfind {

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

    fun normalizeHref(href: String): String =
        Uri.decode(href.trim()).trimEnd('/').lowercase()

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
