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

/** Quick Settings tile: apply the previous wallpaper from local history. */
class PreviousWallpaperTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.qs_tile_previous_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.qs_tile_previous_subtitle)
            }
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            icon = Icon.createWithResource(this@PreviousWallpaperTileService, R.drawable.ic_qs_previous_wallpaper)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (running) return
        running = true
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
        Thread {
            try {
                val err = NextWallpaperApplicator.applyPrevious(applicationContext)
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
            } finally {
                running = false
                mainHandler.post {
                    qsTile?.apply {
                        state = Tile.STATE_INACTIVE
                        updateTile()
                    }
                }
            }
        }.start()
    }
}
