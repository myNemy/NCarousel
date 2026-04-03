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
 * Call from a background thread. All network/Room access runs inside one [runBlocking] (IO).
 *
 * @param orderModeOverride if non-null, used instead of [CarouselPreferences.orderMode] (unsaved UI state).
 * @param wallpaperTargetOverride if non-null, used instead of [CarouselPreferences.wallpaperTarget].
 * @return `null` on success, or a short user-facing error message.
 */
object NextWallpaperApplicator {

    fun applyNext(
        context: Context,
        orderModeOverride: OrderMode? = null,
        wallpaperTargetOverride: WallpaperTarget? = null,
    ): String? =
        runBlocking(Dispatchers.IO) {
            applyNextImpl(context.applicationContext, orderModeOverride, wallpaperTargetOverride)
        }

    private suspend fun applyNextImpl(
        app: Context,
        orderModeOverride: OrderMode?,
        wallpaperTargetOverride: WallpaperTarget?,
    ): String? {
        val active = NextcloudAccountStore(app).getActiveAccount()
            ?: return app.getString(R.string.qs_tile_err_no_account)

        val hrefs = ImageListCache(app, active.id).read().ifEmpty {
            ImageSyncRepository(app).readCachedHrefs(active.id)
        }
        if (hrefs.isEmpty()) return app.getString(R.string.qs_tile_err_no_list)

        val carousel = CarouselPreferences(app)
        val mode = orderModeOverride ?: carousel.orderMode
        val wallpaperTarget = wallpaperTargetOverride ?: carousel.wallpaperTarget
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
        val disk = WallpaperDiskCache(app, active.id, carousel.maxWallpaperDiskCacheMb)
        val href = pick.href
        val bytes = disk.get(href) ?: run {
            val b = client.downloadFile(href).getOrElse { e ->
                return@applyNextImpl e.message?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.qs_tile_err_download)
            }
            disk.put(href, b)
            b
        }

        return WallpaperRepository(app).setWallpaperFromImageBytes(bytes, wallpaperTarget).fold(
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
