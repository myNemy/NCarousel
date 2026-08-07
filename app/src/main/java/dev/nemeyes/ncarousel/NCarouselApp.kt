package dev.nemeyes.ncarousel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dev.nemeyes.ncarousel.work.UnlockPresentReceiver

/**
 * Dynamic [Intent.ACTION_USER_PRESENT] while the process is alive (complements the
 * manifest [UnlockPresentReceiver] for cold starts).
 */
class NCarouselApp : Application() {

    private var unlockReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != Intent.ACTION_USER_PRESENT) return
                UnlockPresentReceiver.onUserPresent(context)
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
