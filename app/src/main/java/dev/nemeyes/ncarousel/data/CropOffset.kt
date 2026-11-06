package dev.nemeyes.ncarousel.data

import android.content.Context
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Normalized framing bias for Cover / Center crop (−1 … +1).
 * `0` is centered; −1 favors the start (left / top); +1 favors the end (right / bottom).
 * Ignored for [WallpaperCropMode.FIT].
 */
data class CropOffset(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    fun coerced(): CropOffset = CropOffset(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))

    fun isCentered(epsilon: Float = 1e-3f): Boolean =
        abs(x) <= epsilon && abs(y) <= epsilon

    companion object {
        val CENTER = CropOffset(0f, 0f)
    }
}

/**
 * Pixel origin of a crop window inside [contentSize] for a [viewSize] viewport and [bias].
 * bias −1 → origin 0; 0 → centered; +1 → max origin.
 */
fun cropOrigin(contentSize: Int, viewSize: Int, bias: Float): Int {
    val maxOrigin = (contentSize - viewSize).coerceAtLeast(0)
    if (maxOrigin == 0) return 0
    val b = bias.coerceIn(-1f, 1f)
    return ((maxOrigin / 2f) * (1f + b)).roundToInt().coerceIn(0, maxOrigin)
}

/**
 * Per-account map of WebDAV href → [CropOffset] for wallpaper framing.
 * Survives list refresh; cleared with the account. Tiny text file (no image data).
 */
class CropOffsetStore(context: Context, private val accountId: String) {

    private val app = context.applicationContext
    private val file: File = app.filesDir.resolve("ncarousel_crop_offsets.$accountId.txt")

    fun get(href: String): CropOffset {
        val key = href.trim()
        if (key.isEmpty()) return CropOffset.CENTER
        return readAll()[key] ?: CropOffset.CENTER
    }

    fun set(href: String, offset: CropOffset) {
        val key = href.trim()
        if (key.isEmpty()) return
        val coerced = offset.coerced()
        val next = readAll().toMutableMap()
        if (coerced.isCentered()) {
            next.remove(key)
        } else {
            next[key] = coerced
        }
        writeAll(next)
    }

    fun remove(href: String) {
        val key = href.trim()
        if (key.isEmpty()) return
        val next = readAll().toMutableMap()
        if (next.remove(key) != null) writeAll(next)
    }

    fun readAll(): Map<String, CropOffset> {
        if (!file.exists()) return emptyMap()
        return parse(file.readText(Charsets.UTF_8))
    }

    fun clear() {
        if (file.exists()) file.delete()
    }

    private fun writeAll(map: Map<String, CropOffset>) {
        file.parentFile?.mkdirs()
        if (map.isEmpty()) {
            clear()
            return
        }
        file.writeText(serialize(map), Charsets.UTF_8)
    }

    companion object {
        const val EXPORT_HEADER = "# NCarousel crop offsets"

        fun parse(text: String): Map<String, CropOffset> {
            val out = LinkedHashMap<String, CropOffset>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val parts = line.split('\t')
                if (parts.size < 3) return@forEach
                val href = parts[0].trim()
                if (href.isEmpty()) return@forEach
                val x = parts[1].toFloatOrNull() ?: return@forEach
                val y = parts[2].toFloatOrNull() ?: return@forEach
                out[href] = CropOffset(x, y).coerced()
            }
            return out
        }

        fun serialize(map: Map<String, CropOffset>): String {
            if (map.isEmpty()) return "$EXPORT_HEADER\n"
            return buildString {
                append(EXPORT_HEADER)
                append('\n')
                map.entries.sortedBy { it.key }.forEach { (href, offset) ->
                    val c = offset.coerced()
                    append(href)
                    append('\t')
                    append(formatBias(c.x))
                    append('\t')
                    append(formatBias(c.y))
                    append('\n')
                }
            }
        }

        private fun formatBias(v: Float): String =
            String.format(java.util.Locale.US, "%.4f", v)
    }
}
