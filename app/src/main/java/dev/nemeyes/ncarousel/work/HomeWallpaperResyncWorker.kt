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
 * Re-applies home **and** lock from the on-disk cache of the last NCarousel wallpaper when the
 * user chose [WallpaperTarget.HOME_AND_LOCK].
 *
 * Used after a successful home+lock apply when launchers/OEMs override or clear
 * one of the surfaces. Skips [WallpaperManager.setBitmap] when wallpaper IDs still match the last
 * apply (avoids a visible black flash). Does not schedule another resync (no loop).
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

        // Still our wallpapers → nothing to fix; rewriting would only flash black.
        if (LastAppliedWallpaperStore.matchesCurrentWallpapers(app)) {
            return@withContext Result.success()
        }

        val accounts = NextcloudAccountStore(app)
        val accountId = inputData.getString(HomeWallpaperResync.KEY_ACCOUNT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: accounts.getActiveAccount()?.id
            ?: return@withContext Result.success()
        val account = accounts.getAccounts().firstOrNull { it.id == accountId }
            ?: accounts.getActiveAccount()
            ?: return@withContext Result.success()

        val href = inputData.getString(HomeWallpaperResync.KEY_HREF)
            ?.takeIf { it.isNotBlank() }
            ?: LastAppliedWallpaperStore.getHref(app, account.id)
            ?: return@withContext Result.success()

        val bytes = WallpaperDiskCache(app, account.id, carousel.maxWallpaperDiskCacheMb).get(href)
            ?: return@withContext Result.success()

        // Re-apply both surfaces (not home-only): home-only rewrite clears/desyncs lock on some OEMs.
        WallpaperRepository(app).setWallpaperFromImageBytes(bytes, WallpaperTarget.HOME_AND_LOCK)
        Result.success()
    }
}
