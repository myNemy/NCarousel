package dev.nemeyes.ncarousel.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * On unlock ([Intent.ACTION_USER_PRESENT]), optionally advances the carousel once
 * with a throttle so rapid lock/unlock does not spam changes.
 *
 * No WorkManager network constraint so cached wallpapers apply offline. Downloads use the
 * normal WebDAV client (same as a manual “next”).
 */
object UnlockWallpaperAdvance {

    private const val WORK_NAME = "ncarousel_unlock_wallpaper_advance"
    private const val SETTLE_DELAY_MS = 1_200L

    /** Never advance on unlock more often than this. */
    const val MIN_THROTTLE_MINUTES = 5

    fun maybeSchedule(context: Context): Boolean {
        val app = context.applicationContext
        val carousel = CarouselPreferences(app)
        if (!carousel.advanceWallpaperOnUnlock) return false
        if (!carousel.autoWallpaperEnabled || carousel.autoWallpaperPaused) return false
        if (!throttleElapsed(carousel)) return false

        val request = OneTimeWorkRequestBuilder<UnlockWallpaperAdvanceWorker>()
            .setInitialDelay(SETTLE_DELAY_MS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return true
    }

    fun throttleElapsed(carousel: CarouselPreferences): Boolean {
        val gapMs = MIN_THROTTLE_MINUTES.toLong() * 60_000L
        val last = carousel.lastUnlockAdvanceEpochMs
        if (last <= 0L) return true
        return System.currentTimeMillis() - last >= gapMs
    }
}

class UnlockWallpaperAdvanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val carousel = CarouselPreferences(app)
        if (!carousel.advanceWallpaperOnUnlock) return@withContext Result.success()
        if (!carousel.autoWallpaperEnabled || carousel.autoWallpaperPaused) return@withContext Result.success()
        if (!UnlockWallpaperAdvance.throttleElapsed(carousel)) return@withContext Result.success()

        val err = NextWallpaperApplicator.applyNextSuspending(app)
        if (err == null) {
            carousel.lastUnlockAdvanceEpochMs = System.currentTimeMillis()
            WallpaperWorkScheduler.scheduleNext(app, ExistingWorkPolicy.REPLACE)
        }
        Result.success()
    }
}

/**
 * Manifest + dynamic registration: unlock must wake WorkManager even if the process was dead.
 * (Dynamic-only registration in [dev.nemeyes.ncarousel.NCarouselApp] is not enough after death.)
 */
class UnlockPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return
        onUserPresent(context)
    }

    companion object {
        fun onUserPresent(context: Context) {
            val scheduledAdvance = UnlockWallpaperAdvance.maybeSchedule(context)
            // Avoid racing OEM resync against an unlock advance (resync @1.5s vs advance @1.2s).
            if (!scheduledAdvance) {
                HomeWallpaperResync.schedule(context)
            }
        }
    }
}
