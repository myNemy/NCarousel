package dev.nemeyes.ncarousel.data

import android.app.WallpaperManager

/** Where [WallpaperManager] applies the bitmap (API 24+ flags; app [minSdk] is 26). */
enum class WallpaperTarget {
    HOME_AND_LOCK,
    HOME_ONLY,
    LOCK_ONLY,
    ;

    fun toWallpaperSetFlags(): Int = when (this) {
        HOME_AND_LOCK -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        HOME_ONLY -> WallpaperManager.FLAG_SYSTEM
        LOCK_ONLY -> WallpaperManager.FLAG_LOCK
    }
}
