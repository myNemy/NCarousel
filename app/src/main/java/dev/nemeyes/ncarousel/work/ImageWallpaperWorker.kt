package dev.nemeyes.ncarousel.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.CarouselStatusNotifications
import dev.nemeyes.ncarousel.data.HttpClientProvider
import dev.nemeyes.ncarousel.data.ImageListCache
import dev.nemeyes.ncarousel.data.ImageSyncRepository
import dev.nemeyes.ncarousel.data.NextcloudWebDavClient
import dev.nemeyes.ncarousel.data.WallpaperDiskCache
import dev.nemeyes.ncarousel.data.WallpaperOrderEngine
import dev.nemeyes.ncarousel.data.WallpaperRepository
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs one wallpaper step; the next run is scheduled in [WallpaperWorkScheduler.scheduleNext] from [finally]
 * so intervals below 15 minutes are possible (unlike [androidx.work.PeriodicWorkRequest]).
 */
class ImageWallpaperWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val carousel = CarouselPreferences(applicationContext)
            if (!carousel.autoWallpaperEnabled) return@withContext Result.success()

            val accounts = NextcloudAccountStore(applicationContext)
            val active = accounts.getActiveAccount() ?: return@withContext Result.failure()

            val maxBytes = carousel.maxImageSizeMb.toLong() * 1024L * 1024L
            val listCache = ImageListCache(applicationContext, active.id)
            var hrefs = listCache.read()
            val http = HttpClientProvider.create(applicationContext)
            val client = NextcloudWebDavClient(
                http,
                active.serverBaseUrl,
                active.userId,
                active.loginName,
                active.appPassword,
            )
            val order = WallpaperOrderEngine(applicationContext, active.id)

            if (hrefs.isEmpty()) {
                // Offline-first: try DB cache first, then network sync.
                val syncRepo = ImageSyncRepository(applicationContext)
                val cached = syncRepo.readCachedHrefs(active.id)
                if (cached.isNotEmpty()) {
                    hrefs = cached
                } else {
                    val listed = syncRepo.syncFromServer(http, active, maxBytes).getOrElse {
                        return@withContext Result.failure()
                    }
                    if (listed.isEmpty()) return@withContext Result.success()
                    hrefs = listed
                }
                order.onLibraryFingerprintChanged(WallpaperOrderEngine.libraryFingerprint(hrefs))
                listCache.write(hrefs) // legacy fast path
            }

            val pick = order.pickWallpaper(hrefs, carousel.orderMode) ?: return@withContext Result.success()
            val href = pick.href

            val disk = WallpaperDiskCache(
                applicationContext,
                active.id,
                carousel.maxWallpaperDiskCacheMb,
            )
            val bytes = disk.get(href) ?: run {
                val b = client.downloadFile(href).getOrElse { return@withContext Result.failure() }
                disk.put(href, b)
                b
            }

            return@withContext WallpaperRepository(applicationContext).setWallpaperFromImageBytes(bytes).fold(
                onSuccess = {
                    pick.commitSuccess()
                    CarouselStatusNotifications.maybeShowWallpaperApplied(
                        applicationContext,
                        carousel,
                        pick.progress,
                        bytes,
                    )
                    Result.success()
                },
                onFailure = { Result.failure() },
            )
        } finally {
            val c = CarouselPreferences(applicationContext)
            val active = NextcloudAccountStore(applicationContext).getActiveAccount()
            if (c.autoWallpaperEnabled && active != null) {
                WallpaperWorkScheduler.scheduleNext(applicationContext)
            }
        }
    }
}
