package dev.nemeyes.ncarousel.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * On unlock ([android.content.Intent.ACTION_USER_PRESENT]), optionally advances the carousel once
 * with a throttle so rapid lock/unlock does not burn mobile data or spam changes.
 */
object UnlockWallpaperAdvance {

    private const val WORK_NAME = "ncarousel_unlock_wallpaper_advance"
    private const val SETTLE_DELAY_MS = 2_500L

    /** Never advance on unlock more often than this, even if the auto interval is 1 minute. */
    const val MIN_THROTTLE_MINUTES = 5

    fun maybeSchedule(context: Context) {
        val app = context.applicationContext
        val carousel = CarouselPreferences(app)
        if (!carousel.advanceWallpaperOnUnlock) return
        if (!carousel.autoWallpaperEnabled || carousel.autoWallpaperPaused) return
        if (!throttleElapsed(carousel)) return

        val networkType = if (carousel.autoWallpaperUnmeteredOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
        val request = OneTimeWorkRequestBuilder<UnlockWallpaperAdvanceWorker>()
            .setInitialDelay(SETTLE_DELAY_MS, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun throttleElapsed(carousel: CarouselPreferences): Boolean {
        val gapMs = max(MIN_THROTTLE_MINUTES, carousel.autoIntervalMinutes).toLong() * 60_000L
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

        val err = NextWallpaperApplicator.applyNext(app)
        if (err == null) {
            carousel.lastUnlockAdvanceEpochMs = System.currentTimeMillis()
            // Restart the timed chain so the next scheduled change is a full interval away.
            WallpaperWorkScheduler.scheduleNext(app, ExistingWorkPolicy.REPLACE)
        }
        Result.success()
    }
}
