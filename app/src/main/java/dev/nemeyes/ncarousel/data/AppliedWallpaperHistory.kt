package dev.nemeyes.ncarousel.data

import android.content.Context
import java.io.File

/**
 * Chronological stack of successfully applied wallpaper hrefs (per account), used for “previous”.
 */
object AppliedWallpaperHistory {

    private const val MAX_ENTRIES = 40

    private fun file(app: Context, accountId: String): File =
        app.filesDir.resolve("ncarousel_applied_history.$accountId.txt")

    fun push(context: Context, accountId: String, href: String) {
        if (href.isBlank()) return
        val app = context.applicationContext
        val f = file(app, accountId)
        val next = (readLines(f) + href).takeLast(MAX_ENTRIES)
        writeLines(f, next)
    }

    fun canGoPrevious(context: Context, accountId: String): Boolean =
        readLines(file(context.applicationContext, accountId)).size >= 2

    /** Href that would become current if the user goes previous, or null. */
    fun peekPrevious(context: Context, accountId: String): String? {
        val lines = readLines(file(context.applicationContext, accountId))
        if (lines.size < 2) return null
        return lines[lines.lastIndex - 1]
    }

    /**
     * After a successful “previous” apply, drop the former tip so history ends on the restored href.
     */
    fun commitPrevious(context: Context, accountId: String) {
        val f = file(context.applicationContext, accountId)
        val lines = readLines(f)
        if (lines.size < 2) return
        writeLines(f, lines.dropLast(1))
    }

    fun clear(context: Context, accountId: String) {
        file(context.applicationContext, accountId).delete()
    }

    private fun readLines(f: File): List<String> {
        if (!f.exists()) return emptyList()
        return f.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun writeLines(f: File, lines: List<String>) {
        f.parentFile?.mkdirs()
        if (lines.isEmpty()) {
            f.delete()
            return
        }
        f.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }
}
