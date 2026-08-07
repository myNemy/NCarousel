package dev.nemeyes.ncarousel.data

import android.content.Context
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccount
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import dev.nemeyes.ncarousel.work.HomeWallpaperResync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Applies the next wallpaper like the in-app action, for use from
 * [android.service.quicksettings.TileService] (no ViewModel / UI).
 *
 * Prefer [applyNextSuspending] from coroutines (workers). The blocking [applyNext] wrapper
 * is for plain background threads only (QS tiles) — never call it from a coroutine already
 * on [Dispatchers.IO] (deadlock risk).
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
            applyNextSuspending(context, orderModeOverride, wallpaperTargetOverride)
        }

    suspend fun applyNextSuspending(
        context: Context,
        orderModeOverride: OrderMode? = null,
        wallpaperTargetOverride: WallpaperTarget? = null,
    ): String? = applyNextImpl(
        context.applicationContext,
        orderModeOverride,
        wallpaperTargetOverride,
    )

    /**
     * Applies a specific wallpaper [href].
     *
     * @param advanceCarousel when true, updates [WallpaperOrderEngine] so the next automatic
     * pick continues after [href] (sequential / shuffle / no-repeat / smart-recent).
     * @return `null` on success, or a short user-facing error message.
     */
    suspend fun applyHref(
        context: Context,
        href: String,
        hrefsForProgress: List<String>,
        wallpaperTargetOverride: WallpaperTarget? = null,
        orderModeOverride: OrderMode? = null,
        advanceCarousel: Boolean = false,
    ): String? = applyHrefImpl(
        context.applicationContext,
        href,
        hrefsForProgress,
        wallpaperTargetOverride,
        orderModeOverride,
        advanceCarousel,
    )

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
        val excluded = ExcludedHrefStore(app, active.id).read()
        val activeHrefs = ExcludedHrefStore.filterActive(hrefs, excluded)
        if (activeHrefs.isEmpty()) return app.getString(R.string.qs_tile_err_all_excluded)
        val pick = WallpaperOrderEngine(app, active.id).pickWallpaper(activeHrefs, mode)
            ?: return app.getString(R.string.qs_tile_err_no_image)

        return downloadAndSet(
            app = app,
            active = active,
            carousel = carousel,
            wallpaperTarget = wallpaperTarget,
            href = pick.href,
            progress = pick.progress,
            onSuccessExtra = { pick.commitSuccess() },
        )
    }

    private suspend fun applyHrefImpl(
        app: Context,
        href: String,
        hrefsForProgress: List<String>,
        wallpaperTargetOverride: WallpaperTarget?,
        orderModeOverride: OrderMode?,
        advanceCarousel: Boolean,
    ): String? {
        val active = NextcloudAccountStore(app).getActiveAccount()
            ?: return app.getString(R.string.qs_tile_err_no_account)
        val carousel = CarouselPreferences(app)
        val wallpaperTarget = wallpaperTargetOverride ?: carousel.wallpaperTarget
        val mode = orderModeOverride ?: carousel.orderMode

        val excluded = ExcludedHrefStore(app, active.id).read()
        val activeHrefs = ExcludedHrefStore.filterActive(hrefsForProgress, excluded)
        val progressHrefs = activeHrefs.ifEmpty { hrefsForProgress }
        val sorted = progressHrefs.sorted()
        val idx0 = sorted.indexOf(href).takeIf { it >= 0 } ?: 0
        var progress = PickProgress(current = (idx0 + 1).coerceAtLeast(1), total = sorted.size.coerceAtLeast(1))

        return downloadAndSet(
            app = app,
            active = active,
            carousel = carousel,
            wallpaperTarget = wallpaperTarget,
            href = href,
            progress = progress,
            onSuccessExtra = {
                if (advanceCarousel && href in activeHrefs.toHashSet()) {
                    WallpaperOrderEngine(app, active.id).advancePast(activeHrefs, mode, href)?.let {
                        progress = it
                    }
                }
            },
            progressProvider = { progress },
        )
    }

    private suspend fun downloadAndSet(
        app: Context,
        active: NextcloudAccount,
        carousel: CarouselPreferences,
        wallpaperTarget: WallpaperTarget,
        href: String,
        progress: PickProgress,
        onSuccessExtra: () -> Unit,
        progressProvider: () -> PickProgress = { progress },
    ): String? {
        val http = HttpClientProvider.create(app)
        val client = NextcloudWebDavClient(
            http,
            active.serverBaseUrl,
            active.userId,
            active.loginName,
            active.appPassword,
        )
        val disk = WallpaperDiskCache(app, active.id, carousel.maxWallpaperDiskCacheMb)
        val bytes = disk.get(href) ?: run {
            val b = client.downloadFile(href).getOrElse { e ->
                return e.message?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.qs_tile_err_download)
            }
            disk.put(href, b)
            b
        }

        return WallpaperRepository(app).setWallpaperFromImageBytes(bytes, wallpaperTarget).fold(
            onSuccess = {
                onSuccessExtra()
                LastAppliedWallpaperStore.setHref(app, active.id, href)
                val place = runCatching { ImageExifPlaceLabel.fromImageBytes(app, bytes, carousel).trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                LastAppliedWallpaperStore.setPlaceLabel(app, active.id, place)
                if (wallpaperTarget == WallpaperTarget.HOME_AND_LOCK) {
                    HomeWallpaperResync.schedule(app, active.id, href)
                }
                CarouselStatusNotifications.maybeShowWallpaperApplied(
                    app,
                    carousel,
                    progressProvider(),
                    placeLabel = place,
                )
                null
            },
            onFailure = { e ->
                e.message?.takeIf { it.isNotBlank() }
                    ?: app.getString(R.string.qs_tile_err_wallpaper)
            },
        )
    }
}
