package dev.nemeyes.ncarousel.data

import android.content.Context
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Applies the next wallpaper like the in-app “Applica prossima immagine” action, for use from
 * [android.service.quicksettings.TileService] (no ViewModel / UI).
 *
 * Call from a background thread; uses [runBlocking] only to read the Room-backed image list when
 * the legacy file cache is empty.
 *
 * @param orderModeOverride if non-null, used instead of [CarouselPreferences.orderMode] (unsaved UI state).
 * @return `null` on success, or a short user-facing error message.
 */
object NextWallpaperApplicator {

    fun applyNext(context: Context, orderModeOverride: OrderMode? = null): String? {
        val app = context.applicationContext
        val active = NextcloudAccountStore(app).getActiveAccount()
            ?: return app.getString(R.string.qs_tile_err_no_account)

        val hrefs = ImageListCache(app, active.id).read().ifEmpty {
            runBlocking(Dispatchers.IO) {
                ImageSyncRepository(app).readCachedHrefs(active.id)
            }
        }
        if (hrefs.isEmpty()) return app.getString(R.string.qs_tile_err_no_list)

        val carousel = CarouselPreferences(app)
        val mode = orderModeOverride ?: carousel.orderMode
        val pick = WallpaperOrderEngine(app, active.id).pickWallpaper(hrefs, mode)
            ?: return app.getString(R.string.qs_tile_err_no_image)

        val http = HttpClientProvider.create(app)
        val client = NextcloudWebDavClient(
            http,
            active.serverBaseUrl,
            active.userId,
            active.loginName,
            active.appPassword,
        )
        val disk = WallpaperDiskCache(app, active.id)
        val href = pick.href
        val bytes = disk.get(href) ?: run {
            val b = client.downloadFile(href).getOrElse { e ->
                return e.message?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.qs_tile_err_download)
            }
            disk.put(href, b)
            b
        }

        return WallpaperRepository(app).setWallpaperFromImageBytes(bytes).fold(
            onSuccess = {
                pick.commitSuccess()
                CarouselStatusNotifications.maybeShowWallpaperApplied(app, carousel, pick.progress, bytes)
                null
            },
            onFailure = { e ->
                e.message?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.qs_tile_err_wallpaper)
            },
        )
    }
}
