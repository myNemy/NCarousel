package dev.nemeyes.ncarousel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.nemeyes.ncarousel.work.HomeWallpaperResync

/**
 * Re-applies the home wallpaper after unlock when the user chose home + lock, so launchers that
 * override [android.app.WallpaperManager.FLAG_SYSTEM] do not leave lock and home out of sync.
 */
class NCarouselApp : Application() {

    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != Intent.ACTION_USER_PRESENT) return
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
