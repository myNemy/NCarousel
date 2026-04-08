package dev.nemeyes.ncarousel.data

import android.content.Context

/**
 * Ricorda l’ultimo file remoto applicato come sfondo da NCarousel, per account (es. EXIF in home).
 */
object LastAppliedWallpaperStore {

    private const val PREFS = "ncarousel_last_wallpaper"

    private fun keyHref(accountId: String) = "href_$accountId"

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

    fun clearForAccount(context: Context, accountId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(keyHref(accountId))
            .apply()
    }
}
