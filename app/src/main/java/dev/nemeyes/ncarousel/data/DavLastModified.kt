package dev.nemeyes.ncarousel.data

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

/**
 * Parses WebDAV `getlastmodified` (typically RFC 1123) to epoch millis.
 * Returns null when missing or unparseable (Library date-sort then falls back).
 */
fun parseDavLastModifiedEpochMs(raw: String?): Long? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    return runCatching {
        ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
}
