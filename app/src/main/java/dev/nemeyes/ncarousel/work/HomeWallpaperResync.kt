package dev.nemeyes.ncarousel.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object HomeWallpaperResync {

    private const val WORK_NAME = "ncarousel_home_wallpaper_resync"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<HomeWallpaperResyncWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
