package dev.nemeyes.ncarousel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.nemeyes.ncarousel.work.HomeWallpaperResync
import dev.nemeyes.ncarousel.work.UnlockWallpaperAdvance

/**
 * After unlock ([Intent.ACTION_USER_PRESENT]):
 * - re-applies home+lock when needed (OEM/launcher drift),
 * - optionally advances the carousel once (throttled) when the user enabled that setting.
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
                UnlockWallpaperAdvance.maybeSchedule(context)
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
