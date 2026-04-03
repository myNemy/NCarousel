package dev.nemeyes.ncarousel.quicksettings

import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator

/**
 * Quick Settings tile: tap to apply the next image (same logic as the in-app button).
 * User must add the tile from the quick-settings edit screen (matita / “Modifica”).
 */
class NextWallpaperTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.qs_tile_wallpaper_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.qs_tile_subtitle)
            }
            state = Tile.STATE_INACTIVE
            icon = Icon.createWithResource(this@NextWallpaperTileService, R.drawable.ic_qs_next_wallpaper)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        Thread {
            val err = NextWallpaperApplicator.applyNext(applicationContext)
            mainHandler.post {
                if (err != null) {
                    Toast.makeText(applicationContext, err, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        R.string.qs_tile_wallpaper_ok,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }.start()
    }
}
