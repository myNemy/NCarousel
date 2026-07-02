package dev.nemeyes.ncarousel.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.LastAppliedWallpaperStore
import dev.nemeyes.ncarousel.data.WallpaperDiskCache
import dev.nemeyes.ncarousel.data.WallpaperRepository
import dev.nemeyes.ncarousel.data.WallpaperTarget
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Re-applies [WallpaperTarget.HOME_ONLY] from the on-disk cache of the last NCarousel wallpaper.
 * Used after unlock and after a home+lock apply when launchers override the system wallpaper.
 */
class HomeWallpaperResyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        val carousel = CarouselPreferences(app)
        if (carousel.wallpaperTarget != WallpaperTarget.HOME_AND_LOCK) {
            return@withContext Result.success()
        }
        val account = NextcloudAccountStore(app).getActiveAccount()
            ?: return@withContext Result.success()
        val href = LastAppliedWallpaperStore.getHref(app, account.id)
            ?: return@withContext Result.success()
        val bytes = WallpaperDiskCache(app, account.id, carousel.maxWallpaperDiskCacheMb).get(href)
            ?: return@withContext Result.success()

        WallpaperRepository(app).setWallpaperFromImageBytes(bytes, WallpaperTarget.HOME_ONLY)
        // Best-effort: launchers may still win; do not fail/retry aggressively.
        Result.success()
    }
}
