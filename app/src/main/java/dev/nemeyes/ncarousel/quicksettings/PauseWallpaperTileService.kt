package dev.nemeyes.ncarousel.quicksettings

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.widget.NCarouselAppWidget
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings toggle: pause / resume automatic wallpaper changes without disabling the feature.
 */
class PauseWallpaperTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val carousel = CarouselPreferences(applicationContext)
        if (!carousel.autoWallpaperEnabled) {
            Toast.makeText(
                applicationContext,
                R.string.qs_tile_err_auto_disabled,
                Toast.LENGTH_SHORT,
            ).show()
            refreshTile()
            return
        }
        val nowPaused = !carousel.autoWallpaperPaused
        carousel.autoWallpaperPaused = nowPaused
        WallpaperWorkScheduler.sync(applicationContext, ExistingWorkPolicy.REPLACE)
        Toast.makeText(
            applicationContext,
            if (nowPaused) R.string.qs_tile_pause_on else R.string.qs_tile_pause_off,
            Toast.LENGTH_SHORT,
        ).show()
        refreshTile()
        scope.launch {
            runCatching { NCarouselAppWidget.updateAll(applicationContext) }
        }
    }

    private fun refreshTile() {
        val carousel = CarouselPreferences(applicationContext)
        val paused = carousel.autoWallpaperEnabled && carousel.autoWallpaperPaused
        qsTile?.apply {
            label = getString(R.string.qs_tile_pause_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (paused) R.string.qs_tile_pause_subtitle_on else R.string.qs_tile_pause_subtitle_off,
                )
            }
            state = when {
                !carousel.autoWallpaperEnabled -> Tile.STATE_UNAVAILABLE
                paused -> Tile.STATE_ACTIVE
                else -> Tile.STATE_INACTIVE
            }
            icon = Icon.createWithResource(this@PauseWallpaperTileService, R.drawable.ic_qs_pause_wallpaper)
            updateTile()
        }
    }
}
