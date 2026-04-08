package dev.nemeyes.ncarousel.data

import android.content.Context

/**
 * Non-secret carousel options (interval, mode). Accounts stay in [dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore].
 */
class CarouselPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var orderMode: OrderMode
        get() = prefs.getString(KEY_ORDER, OrderMode.RANDOM.name)?.let { runCatching { OrderMode.valueOf(it) }.getOrNull() }
            ?: OrderMode.RANDOM
        set(value) { prefs.edit().putString(KEY_ORDER, value.name).apply() }

    /** Home, lock, or both ([WallpaperManager] flags). */
    var wallpaperTarget: WallpaperTarget
        get() = prefs.getString(KEY_WALLPAPER_TARGET, null)
            ?.let { runCatching { WallpaperTarget.valueOf(it) }.getOrNull() }
            ?: WallpaperTarget.HOME_AND_LOCK
        set(value) { prefs.edit().putString(KEY_WALLPAPER_TARGET, value.name).apply() }

    /** 0 = no limit (bytes not checked or unknown sizes kept). */
    var maxImageSizeMb: Int
        get() = prefs.getInt(KEY_MAX_MB, 0).coerceAtLeast(0)
        set(value) { prefs.edit().putInt(KEY_MAX_MB, value.coerceAtLeast(0)).apply() }

    /**
     * Max total size for on-disk wallpaper image cache per account ([WallpaperDiskCache]).
     * LRU eviction; stored under app cache (Android may clear it under pressure).
     */
    var maxWallpaperDiskCacheMb: Int
        get() = prefs.getInt(KEY_DISK_CACHE_MB, WallpaperDiskCache.DEFAULT_MAX_MB)
            .coerceIn(WallpaperDiskCache.MIN_MB, WallpaperDiskCache.MAX_MB)
        set(value) {
            prefs.edit().putInt(
                KEY_DISK_CACHE_MB,
                value.coerceIn(WallpaperDiskCache.MIN_MB, WallpaperDiskCache.MAX_MB),
            ).apply()
        }

    var autoWallpaperEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO, value).apply() }

    /** Desired interval between automatic wallpaper runs (minimum 1 minute via chained one-shot work). */
    var autoIntervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_MIN, 30).coerceAtLeast(1)
        set(value) { prefs.edit().putInt(KEY_INTERVAL_MIN, value.coerceAtLeast(1)).apply() }

    /** Abilita le notifiche di stato (master); i sotto-tipi sono indipendenti se true. */
    var showStatusNotifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_STATUS, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIFY_STATUS, value).apply() }

    /** Notifica dopo cambio sfondo (manuale o automatico). */
    var notifyWallpaperApplied: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_WALLPAPER, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIFY_WALLPAPER, value).apply() }

    /** Notifica dopo aggiornamento elenco immagini da app. */
    var notifyLibraryRefreshed: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_LIST, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIFY_LIST, value).apply() }

    /**
     * Se true, nella notifica sfondo si aggiunge luogo (EXIF + geocoding, richiede rete).
     * Se false, solo progressivo nell’elenco (nessuna chiamata Nominatim/Photon/Geocoder).
     */
    var notifyWallpaperIncludeLocation: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_PLACE, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIFY_PLACE, value).apply() }

    /** Nominatim (OpenStreetMap) nel reverse geocoding per le notifiche con luogo. */
    var geocoderNominatimEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEO_NOMINATIM, true)
        set(value) { prefs.edit().putBoolean(KEY_GEO_NOMINATIM, value).apply() }

    /** [android.location.Geocoder] del dispositivo (es. Google con GMS). */
    var geocoderPlatformEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEO_PLATFORM, true)
        set(value) { prefs.edit().putBoolean(KEY_GEO_PLATFORM, value).apply() }

    /** Photon (istanza pubblica Komoot, dati OSM). */
    var geocoderPhotonEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEO_PHOTON, true)
        set(value) { prefs.edit().putBoolean(KEY_GEO_PHOTON, value).apply() }

    var geocoderOrderMode: GeocoderOrderMode
        get() = prefs.getString(KEY_GEO_ORDER, null)
            ?.let { runCatching { GeocoderOrderMode.valueOf(it) }.getOrNull() }
            ?: GeocoderOrderMode.NOMINATIM_FIRST
        set(value) { prefs.edit().putString(KEY_GEO_ORDER, value.name).apply() }

    /**
     * After [completeInitialConsentFlow] runs once, the app stops showing the first-launch consent dialog.
     */
    var initialConsentFlowCompleted: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_CONSENT_DONE, false)
        set(value) { prefs.edit().putBoolean(KEY_INITIAL_CONSENT_DONE, value).apply() }

    /** Cached OCS theming [color] for [accountId] (hex, e.g. `#0082c9`). */
    fun getThemingPrimaryHex(accountId: String): String? =
        prefs.getString(KEY_THEMING_COLOR + sanitizeAccountKey(accountId), null)?.takeIf { it.isNotBlank() }

    /** Cached OCS theming [color-text] for primary surfaces. */
    fun getThemingOnPrimaryHex(accountId: String): String? =
        prefs.getString(KEY_THEMING_ON_PRIMARY + sanitizeAccountKey(accountId), null)?.takeIf { it.isNotBlank() }

    fun setThemingForAccount(accountId: String, color: String, colorText: String?) {
        val k = sanitizeAccountKey(accountId)
        val ed = prefs.edit().putString(KEY_THEMING_COLOR + k, color)
        if (colorText != null) ed.putString(KEY_THEMING_ON_PRIMARY + k, colorText)
        else ed.remove(KEY_THEMING_ON_PRIMARY + k)
        ed.apply()
    }

    fun clearThemingForAccount(accountId: String) {
        val k = sanitizeAccountKey(accountId)
        prefs.edit()
            .remove(KEY_THEMING_COLOR + k)
            .remove(KEY_THEMING_ON_PRIMARY + k)
            .apply()
    }

    private fun sanitizeAccountKey(accountId: String): String =
        accountId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)

    companion object {
        private const val PREFS = "ncarousel_carousel"
        private const val KEY_ORDER = "order_mode"
        private const val KEY_WALLPAPER_TARGET = "wallpaper_target"
        private const val KEY_MAX_MB = "max_image_mb"
        private const val KEY_DISK_CACHE_MB = "max_wallpaper_disk_cache_mb"
        private const val KEY_AUTO = "auto_wallpaper"
        private const val KEY_INTERVAL_MIN = "auto_interval_minutes"
        private const val KEY_NOTIFY_STATUS = "show_status_notifications"
        private const val KEY_NOTIFY_WALLPAPER = "notify_wallpaper_applied"
        private const val KEY_NOTIFY_LIST = "notify_library_refreshed"
        private const val KEY_NOTIFY_PLACE = "notify_wallpaper_include_location"
        private const val KEY_GEO_NOMINATIM = "geocoder_nominatim_enabled"
        private const val KEY_GEO_PLATFORM = "geocoder_platform_enabled"
        private const val KEY_GEO_PHOTON = "geocoder_photon_enabled"
        private const val KEY_GEO_ORDER = "geocoder_order_mode"
        private const val KEY_INITIAL_CONSENT_DONE = "initial_consent_flow_completed"
        private const val KEY_THEMING_COLOR = "theming_color_"
        private const val KEY_THEMING_ON_PRIMARY = "theming_on_primary_"
    }
}
