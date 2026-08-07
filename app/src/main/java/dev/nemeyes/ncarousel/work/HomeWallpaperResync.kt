package dev.nemeyes.ncarousel.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Re-applies the last NCarousel wallpaper after OEMs/launchers override home or clear lock.
 *
 * Always pass [accountId] + [href] when scheduling right after an apply (avoids racing
 * [dev.nemeyes.ncarousel.data.LastAppliedWallpaperStore]). Unlock may omit them and use the store.
 */
object HomeWallpaperResync {

    private const val WORK_NAME = "ncarousel_home_wallpaper_resync"

    const val KEY_ACCOUNT_ID = "account_id"
    const val KEY_HREF = "href"

    /** Delay so launcher/OEM wallpaper pipelines finish before we rewrite both surfaces. */
    private const val SETTLE_DELAY_MS = 1_500L

    fun schedule(
        context: Context,
        accountId: String? = null,
        href: String? = null,
    ) {
        val data = Data.Builder().apply {
            if (!accountId.isNullOrBlank()) putString(KEY_ACCOUNT_ID, accountId)
            if (!href.isNullOrBlank()) putString(KEY_HREF, href)
        }.build()
        val request = OneTimeWorkRequestBuilder<HomeWallpaperResyncWorker>()
            .setInputData(data)
            .setInitialDelay(SETTLE_DELAY_MS, TimeUnit.MILLISECONDS)
            .build()
        // REPLACE: latest apply wins (KEEP could leave a stale href from an older enqueue).
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
