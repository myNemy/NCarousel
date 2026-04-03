package dev.nemeyes.ncarousel.data

internal data class DavEntry(
    val hrefDecoded: String,
    val isCollection: Boolean,
    val contentType: String?,
    /** From WebDAV `getcontentlength`, when present. */
    val contentLengthBytes: Long? = null,
    /** From WebDAV `getetag`, when present. */
    val etag: String? = null,
    /** From WebDAV `getlastmodified`, when present. */
    val lastModified: String? = null,
)
