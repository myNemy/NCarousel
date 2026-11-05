package dev.nemeyes.ncarousel.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Writes image bytes to a short-lived cache file and exposes a [FileProvider] content URI
 * for Android’s share sheet. Never includes Nextcloud URLs or credentials.
 */
object ImageShareHelper {

    const val AUTHORITY = "dev.nemeyes.ncarousel.fileprovider"

    fun shareDir(context: Context): File =
        context.applicationContext.cacheDir.resolve("share").also { it.mkdirs() }

    fun clearShareDir(context: Context) {
        shareDir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    fun writeShareFile(context: Context, displayName: String, bytes: ByteArray): File {
        clearShareDir(context)
        val file = File(shareDir(context), sanitizeFileName(displayName))
        file.writeBytes(bytes)
        return file
    }

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context.applicationContext, AUTHORITY, file)

    fun mimeTypeForFileName(displayName: String): String {
        val lower = displayName.lowercase(Locale.US)
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            else -> "image/*"
        }
    }

    fun sanitizeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        return when {
            cleaned.isBlank() -> "image.jpg"
            cleaned.contains('.') -> cleaned
            else -> "$cleaned.jpg"
        }
    }
}
