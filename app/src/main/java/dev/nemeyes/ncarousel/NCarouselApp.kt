package dev.nemeyes.ncarousel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.nemeyes.ncarousel.work.HomeWallpaperResync

/**
 * Re-applies home + lock wallpaper after unlock when the user chose home + lock, so launchers/OEMs
 * that override [android.app.WallpaperManager.FLAG_SYSTEM] or clear lock do not leave surfaces
 * out of sync.
 */
class NCarouselApp : Application() {

    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != Intent.ACTION_USER_PRESENT) return
                // Uses LastApplied store; delayed inside HomeWallpaperResync.
                HomeWallpaperResync.schedule(context)
            }
        }
        unlockReceiver = receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }
}
