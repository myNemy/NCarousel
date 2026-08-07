package dev.nemeyes.ncarousel.data

import android.app.WallpaperManager
import android.content.Context

/**
 * Ricorda l’ultimo file remoto applicato come sfondo da NCarousel, per account (es. EXIF in home),
 * e gli ID wallpaper di sistema usati per saltare un resync ridondante (niente flash nero).
 */
object LastAppliedWallpaperStore {

    private const val PREFS = "ncarousel_last_wallpaper"
    private const val KEY_SYSTEM_WALLPAPER_ID = "system_wallpaper_id"
    private const val KEY_LOCK_WALLPAPER_ID = "lock_wallpaper_id"
    private const val ID_UNSET = Int.MIN_VALUE

    private fun keyHref(accountId: String) = "href_$accountId"
    private fun keyPlace(accountId: String) = "place_$accountId"

    fun getHref(context: Context, accountId: String): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyHref(accountId), null)
            ?.takeIf { it.isNotBlank() }

    fun setHref(context: Context, accountId: String, href: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(keyHref(accountId), href)
            .apply()
    }

    fun getPlaceLabel(context: Context, accountId: String): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(keyPlace(accountId), null)
            ?.takeIf { it.isNotBlank() }

    fun setPlaceLabel(context: Context, accountId: String, label: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (label.isNullOrBlank()) remove(keyPlace(accountId)) else putString(keyPlace(accountId), label)
            }
            .apply()
    }

    /** Snapshot [WallpaperManager.getWallpaperId] after a successful NCarousel apply (device-wide). */
    fun setAppliedWallpaperIds(context: Context, systemId: Int, lockId: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SYSTEM_WALLPAPER_ID, systemId)
            .putInt(KEY_LOCK_WALLPAPER_ID, lockId)
            .apply()
    }

    fun clearAppliedWallpaperIds(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SYSTEM_WALLPAPER_ID)
            .remove(KEY_LOCK_WALLPAPER_ID)
            .apply()
    }

    /**
     * True when home (and lock, if we recorded a positive lock id) still match the last apply.
     * Used by OEM resync to avoid rewriting the same bitmap (visible black flash).
     */
    fun matchesCurrentWallpapers(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val expectedSystem = prefs.getInt(KEY_SYSTEM_WALLPAPER_ID, ID_UNSET)
        if (expectedSystem == ID_UNSET) return false
        val expectedLock = prefs.getInt(KEY_LOCK_WALLPAPER_ID, ID_UNSET)
        val wm = WallpaperManager.getInstance(context.applicationContext)
        val currentSystem = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }
            .getOrDefault(ID_UNSET)
        if (currentSystem != expectedSystem) return false
        if (expectedLock == ID_UNSET) return true
        val currentLock = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_LOCK) }
            .getOrDefault(ID_UNSET)
        // Shared home/lock engine often reports lock id ≤ 0; treat as still matching.
        if (expectedLock <= 0) return currentLock <= 0 || currentLock == currentSystem
        if (currentLock <= 0) return false
        return currentLock == expectedLock
    }

    fun clearForAccount(context: Context, accountId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(keyHref(accountId))
            .remove(keyPlace(accountId))
            .apply()
    }
}
