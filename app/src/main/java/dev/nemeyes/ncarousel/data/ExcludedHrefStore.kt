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
        return parse(file.readText(Charsets.UTF_8))
    }

    fun write(hrefs: Set<String>) {
        file.parentFile?.mkdirs()
        if (hrefs.isEmpty()) {
            clear()
            return
        }
        file.writeText(serialize(hrefs), Charsets.UTF_8)
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
        const val EXPORT_HEADER = "# NCarousel excluded hrefs"
        /** Hidden app folder under the account remote folder (Nextcloud). */
        const val REMOTE_DIR_NAME = ".ncarousel"
        const val REMOTE_FILE_NAME = "excluded-hrefs.txt"

        fun filterActive(hrefs: List<String>, excluded: Set<String>): List<String> =
            if (excluded.isEmpty()) hrefs else hrefs.filter { it !in excluded }

        /** One href per line; optional `#` comment header/lines; blank lines ignored. */
        fun parse(text: String): Set<String> =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()

        fun serialize(hrefs: Set<String>): String {
            if (hrefs.isEmpty()) return "$EXPORT_HEADER\n"
            return buildString {
                append(EXPORT_HEADER)
                append('\n')
                hrefs.sorted().forEach { href ->
                    append(href)
                    append('\n')
                }
            }
        }

        /** Relative DAV path for the excluded list under [remoteFolder]. */
        fun remoteFilePath(remoteFolder: String): String {
            val root = remoteFolder.trim().trim('/')
            return if (root.isEmpty()) {
                "$REMOTE_DIR_NAME/$REMOTE_FILE_NAME"
            } else {
                "$root/$REMOTE_DIR_NAME/$REMOTE_FILE_NAME"
            }
        }

        fun remoteDirPath(remoteFolder: String): String {
            val root = remoteFolder.trim().trim('/')
            return if (root.isEmpty()) REMOTE_DIR_NAME else "$root/$REMOTE_DIR_NAME"
        }
    }
}
