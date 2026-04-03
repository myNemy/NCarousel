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

    /** Show notifications after list refresh / wallpaper change (count + EXIF place name when available). */
    var showStatusNotifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_STATUS, true)
        set(value) { prefs.edit().putBoolean(KEY_NOTIFY_STATUS, value).apply() }

    /**
     * After [completeInitialConsentFlow] runs once, the app stops showing the first-launch consent dialog.
     */
    var initialConsentFlowCompleted: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_CONSENT_DONE, false)
        set(value) { prefs.edit().putBoolean(KEY_INITIAL_CONSENT_DONE, value).apply() }

    companion object {
        private const val PREFS = "ncarousel_carousel"
        private const val KEY_ORDER = "order_mode"
        private const val KEY_MAX_MB = "max_image_mb"
        private const val KEY_DISK_CACHE_MB = "max_wallpaper_disk_cache_mb"
        private const val KEY_AUTO = "auto_wallpaper"
        private const val KEY_INTERVAL_MIN = "auto_interval_minutes"
        private const val KEY_NOTIFY_STATUS = "show_status_notifications"
        private const val KEY_INITIAL_CONSENT_DONE = "initial_consent_flow_completed"
    }
}
