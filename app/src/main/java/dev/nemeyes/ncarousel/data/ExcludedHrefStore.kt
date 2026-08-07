package dev.nemeyes.ncarousel.data

import android.content.Context
import java.io.File

/**
 * Per-account blacklist of WebDAV hrefs excluded from automatic carousel picks
 * (WorkManager / “next” / QS tile). Manual Library apply is unaffected.
 *
 * Survives list refresh (Room / [ImageListCache] rewrites); cleared with the account.
 */
class ExcludedHrefStore(context: Context, private val accountId: String) {

    private val app = context.applicationContext
    private val file: File = app.filesDir.resolve("ncarousel_excluded_hrefs.$accountId.txt")

    fun read(): Set<String> {
        if (!file.exists()) return emptySet()
        return file.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun write(hrefs: Set<String>) {
        file.parentFile?.mkdirs()
        if (hrefs.isEmpty()) {
            clear()
            return
        }
        file.writeText(hrefs.sorted().joinToString("\n"), Charsets.UTF_8)
    }

    /** @return true if [href] is excluded after the toggle. */
    fun toggle(href: String): Boolean {
        val key = href.trim()
        if (key.isEmpty()) return false
        val next = read().toMutableSet()
        val nowExcluded = if (key in next) {
            next.remove(key)
            false
        } else {
            next.add(key)
            true
        }
        write(next)
        return nowExcluded
    }

    fun clear() {
        if (file.exists()) file.delete()
    }

    companion object {
        fun filterActive(hrefs: List<String>, excluded: Set<String>): List<String> =
            if (excluded.isEmpty()) hrefs else hrefs.filter { it !in excluded }
    }
}
