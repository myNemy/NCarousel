package dev.nemeyes.ncarousel.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import java.util.concurrent.TimeUnit

/**
 * Schedules repeating wallpaper work with [OneTimeWorkRequest] + [androidx.work.WorkManager.enqueueUniqueWork],
 * because [androidx.work.PeriodicWorkRequest] cannot run faster than 15 minutes
 * ([PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)).
 * Each [ImageWallpaperWorker] run enqueues the next execution after [CarouselPreferences.autoIntervalMinutes]
 * (minimum [MIN_INTERVAL_MINUTES]).
 *
 * [sync] uses [ExistingWorkPolicy.KEEP] by default so reopening the app does not reset the delay; pass
 * [ExistingWorkPolicy.REPLACE] after changing interval or account so the new schedule applies immediately.
 */
object WallpaperWorkScheduler {

    private const val UNIQUE_CHAIN = "ncarousel_wallpaper_chain"

    /** Previous periodic unique name; cancelled so old installs stop the 15-minute job. */
    private const val LEGACY_PERIODIC_UNIQUE = "ncarousel_periodic_wallpaper"

    /** Minimum interval supported by this app (one-shot chain, not periodic API). */
    const val MIN_INTERVAL_MINUTES = 1

    fun sync(
        context: Context,
        pendingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        wm.cancelUniqueWork(LEGACY_PERIODIC_UNIQUE)

        val carousel = CarouselPreferences(app)
        if (!carousel.autoWallpaperEnabled) {
            wm.cancelUniqueWork(UNIQUE_CHAIN)
            return
        }
        scheduleNext(app, pendingWorkPolicy)
    }

    /**
     * Enqueues a single [ImageWallpaperWorker] after the configured delay, replacing any pending chain work.
     */
    fun scheduleNext(
        context: Context,
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        val app = context.applicationContext
        val carousel = CarouselPreferences(app)
        if (!carousel.autoWallpaperEnabled) return

        val active = NextcloudAccountStore(app).getActiveAccount()
        if (active == null) return

        val minutes = carousel.autoIntervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ImageWallpaperWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_CHAIN,
            existingWorkPolicy,
            request,
        )
    }
}
