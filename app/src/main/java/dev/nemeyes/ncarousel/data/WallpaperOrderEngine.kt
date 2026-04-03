package dev.nemeyes.ncarousel.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Picks the next image href according to [OrderMode]. State advances only when
 * [WallpaperPick.commitSuccess] runs (after the wallpaper is applied successfully).
 */
class WallpaperOrderEngine(context: Context, private val accountId: String) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("$PREFS_ORDER.$accountId", Context.MODE_PRIVATE)
    private val orderDir: File = app.filesDir.resolve("ncarousel_order/$accountId").also { it.mkdirs() }
    private val noRepeatFile = orderDir.resolve("no_repeat_queue.txt")
    private val smartRecentFile = orderDir.resolve("smart_recent.txt")

    /** Call when the remote list was refreshed successfully so fingerprints can reset modes. */
    fun onLibraryFingerprintChanged(fingerprint: String) {
        val prev = prefs.getString(KEY_LIST_FP, null)
        if (prev == fingerprint) return
        prefs.edit()
            .putString(KEY_LIST_FP, fingerprint)
            .putInt(KEY_SEQ_INDEX, 0)
            .putLong(KEY_SHUFFLE_SEED, Random.Default.nextLong())
            .putInt(KEY_SHUFFLE_WALK, 0)
            .apply()
        noRepeatFile.delete()
        smartRecentFile.delete()
    }

    fun pickWallpaper(hrefs: List<String>, mode: OrderMode): WallpaperPick? {
        if (hrefs.isEmpty()) return null
        val sorted = hrefs.sorted()
        return when (mode) {
            OrderMode.SEQUENTIAL -> sequentialPick(sorted)
            OrderMode.RANDOM -> WallpaperPick(sorted[Random.Default.nextInt(sorted.size)], onCommit = { })
            OrderMode.SHUFFLE_ONCE -> shuffleOncePick(sorted)
            OrderMode.SMART_RANDOM -> smartRandomPick(sorted)
            OrderMode.NO_REPEAT_SHUFFLE -> noRepeatPick(sorted)
        }
    }

    private fun sequentialPick(sorted: List<String>): WallpaperPick {
        val old = prefs.getInt(KEY_SEQ_INDEX, 0).coerceAtLeast(0)
        val i = old % sorted.size
        val href = sorted[i]
        return WallpaperPick(href, onCommit = {
            prefs.edit().putInt(KEY_SEQ_INDEX, (old + 1) % sorted.size).apply()
        })
    }

    private fun shuffleOncePick(sorted: List<String>): WallpaperPick {
        val seed = prefs.getLong(KEY_SHUFFLE_SEED, Random.Default.nextLong()).also {
            if (!prefs.contains(KEY_SHUFFLE_SEED)) {
                prefs.edit().putLong(KEY_SHUFFLE_SEED, it).apply()
            }
        }
        val order = sorted.shuffled(Random(seed))
        val old = prefs.getInt(KEY_SHUFFLE_WALK, 0).coerceAtLeast(0)
        val i = old % order.size
        val href = order[i]
        return WallpaperPick(href, onCommit = {
            prefs.edit().putInt(KEY_SHUFFLE_WALK, (old + 1) % order.size).apply()
        })
    }

    private fun smartRandomPick(sorted: List<String>): WallpaperPick {
        val recent = readLines(smartRecentFile).toMutableSet()
        val candidates = sorted.filter { it !in recent }
        val pool = if (candidates.isNotEmpty()) candidates else sorted
        val pick = pool[Random.Default.nextInt(pool.size)]
        return WallpaperPick(pick, onCommit = {
            val lines = (listOf(pick) + readLines(smartRecentFile)).distinct().take(SMART_WINDOW)
            smartRecentFile.writeText(lines.joinToString("\n"), Charsets.UTF_8)
        })
    }

    private fun noRepeatPick(sorted: List<String>): WallpaperPick? {
        var lines = readLines(noRepeatFile).filter { it in sorted.toSet() }
        if (lines.isEmpty()) {
            lines = sorted.shuffled(Random.Default)
            writeLines(noRepeatFile, lines)
        }
        val href = lines.firstOrNull() ?: return null
        return WallpaperPick(href, onCommit = {
            val rest = readLines(noRepeatFile).filter { it in sorted.toSet() }.drop(1)
            if (rest.isEmpty()) {
                noRepeatFile.delete()
            } else {
                writeLines(noRepeatFile, rest)
            }
        })
    }

    private fun readLines(f: File): List<String> {
        if (!f.exists()) return emptyList()
        return f.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun writeLines(f: File, lines: List<String>) {
        f.writeText(lines.joinToString("\n"), Charsets.UTF_8)
    }

    companion object {
        private const val PREFS_ORDER = "ncarousel_order_state"
        private const val KEY_LIST_FP = "library_fingerprint"
        private const val KEY_SEQ_INDEX = "seq_index"
        private const val KEY_SHUFFLE_SEED = "shuffle_seed"
        private const val KEY_SHUFFLE_WALK = "shuffle_walk"
        private const val SMART_WINDOW = 12

        fun libraryFingerprint(hrefs: List<String>): String {
            val md = MessageDigest.getInstance("SHA-256")
            hrefs.sorted().forEach { line ->
                md.update(line.toByteArray(Charsets.UTF_8))
                md.update(0xa)
            }
            return md.digest().joinToString("") { b -> "%02x".format(b) }
        }
    }
}

data class WallpaperPick(
    val href: String,
    private val onCommit: () -> Unit,
) {
    fun commitSuccess() {
        onCommit()
    }
}
