package dev.nemeyes.ncarousel.data

import android.content.Context
import java.io.File

/**
 * Persists the last scanned image href list so [ImageWallpaperWorker] can run without the UI.
 */
class ImageListCache(context: Context, private val accountId: String) {

    private val app = context.applicationContext
    private val file: File = app.filesDir.resolve("ncarousel_image_hrefs.$accountId.cache")

    fun write(hrefs: List<String>) {
        file.parentFile?.mkdirs()
        file.writeText(hrefs.joinToString("\u0000") { it.replace('\u0000', ' ') }, Charsets.UTF_8)
    }

    fun read(): List<String> {
        if (!file.exists()) return emptyList()
        val text = file.readText(Charsets.UTF_8)
        if (text.isEmpty()) return emptyList()
        return text.split('\u0000').filter { it.isNotEmpty() }
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
