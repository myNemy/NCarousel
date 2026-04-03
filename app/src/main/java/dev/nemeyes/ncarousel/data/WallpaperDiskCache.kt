package dev.nemeyes.ncarousel.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Bounded on-disk cache for raw image bytes (LRU by last access via file timestamp).
 * Lives under [Context.getCacheDir]: the system may clear it when storage is low.
 *
 * @param maxCacheMb clamped to [[MIN_MB], [MAX_MB]]; typical default from [CarouselPreferences].
 */
class WallpaperDiskCache(
    context: Context,
    private val accountId: String,
    maxCacheMb: Int,
) {

    private val dir: File = cacheDir(context.applicationContext, accountId).also { it.mkdirs() }
    private val maxBytes: Long = maxCacheMb.coerceIn(MIN_MB, MAX_MB).toLong() * 1024L * 1024L

    fun get(href: String): ByteArray? {
        val f = fileFor(href)
        if (!f.exists() || f.length() == 0L) return null
        f.setLastModified(System.currentTimeMillis())
        return f.readBytes()
    }

    fun put(href: String, bytes: ByteArray) {
        evictFor(bytes.size.toLong())
        fileFor(href).writeBytes(bytes)
        trimToBudget()
    }

    private fun evictFor(incoming: Long) {
        while (totalSize() + incoming > maxBytes && dir.exists()) {
            val victim = dir.listFiles()?.minByOrNull { it.lastModified() } ?: break
            if (!victim.delete()) break
        }
    }

    private fun trimToBudget() {
        while (totalSize() > maxBytes && dir.exists()) {
            val victim = dir.listFiles()?.minByOrNull { it.lastModified() } ?: break
            if (!victim.delete()) break
        }
    }

    private fun totalSize(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun fileFor(href: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(accountId.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(href.toByteArray(Charsets.UTF_8))
        val hex = digest.digest().joinToString("") { b -> "%02x".format(b) }
        return File(dir, hex)
    }

    /** Drop files until under [maxBytes] (e.g. after user lowers the limit in settings). */
    fun enforceBudget() {
        trimToBudget()
    }

    companion object {
        const val DEFAULT_MAX_MB = 150
        const val MIN_MB = 16
        const val MAX_MB = 512

        fun cacheDir(app: Context, accountId: String): File =
            app.applicationContext.cacheDir.resolve("ncarousel_wallpaper_cache/$accountId")

        /** Deletes cached image files for [accountId] (next download will refetch). */
        fun clear(app: Context, accountId: String) {
            val d = cacheDir(app, accountId)
            d.listFiles()?.forEach { f -> runCatching { f.delete() } }
        }
    }
}
