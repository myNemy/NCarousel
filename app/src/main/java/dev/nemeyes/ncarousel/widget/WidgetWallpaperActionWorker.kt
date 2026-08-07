package dev.nemeyes.ncarousel.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs widget Next/Previous/Pause off the Glance action binder (downloads can exceed its budget).
 */
object WidgetWallpaperActions {
    const val WORK_NAME = "ncarousel_widget_wallpaper_action"
    const val KEY_ACTION = "action"
    const val ACTION_NEXT = "next"
    const val ACTION_PREVIOUS = "previous"
    const val ACTION_PAUSE = "pause"

    fun enqueue(context: Context, action: String) {
        val request = OneTimeWorkRequestBuilder<WidgetWallpaperActionWorker>()
            .setInputData(Data.Builder().putString(KEY_ACTION, action).build())
            .build()
        // REPLACE: latest tap wins if the user hammers buttons.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class WidgetWallpaperActionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext
        when (inputData.getString(WidgetWallpaperActions.KEY_ACTION)) {
            WidgetWallpaperActions.ACTION_NEXT -> {
                val err = NextWallpaperApplicator.applyNextSuspending(app)
                toast(app, err, okRes = R.string.qs_tile_wallpaper_ok)
            }
            WidgetWallpaperActions.ACTION_PREVIOUS -> {
                val err = NextWallpaperApplicator.applyPreviousSuspending(app)
                toast(app, err, okRes = R.string.qs_tile_wallpaper_ok)
            }
            WidgetWallpaperActions.ACTION_PAUSE -> {
                val carousel = CarouselPreferences(app)
                if (!carousel.autoWallpaperEnabled) {
                    toast(app, app.getString(R.string.qs_tile_err_auto_disabled), okRes = null)
                } else {
                    val nowPaused = !carousel.autoWallpaperPaused
                    carousel.autoWallpaperPaused = nowPaused
                    WallpaperWorkScheduler.sync(app, ExistingWorkPolicy.REPLACE)
                    toast(
                        app,
                        null,
                        okRes = if (nowPaused) R.string.qs_tile_pause_on else R.string.qs_tile_pause_off,
                    )
                }
                runCatching { NCarouselAppWidget.updateAll(app) }
            }
            else -> Unit
        }
        Result.success()
    }

    private fun toast(app: Context, err: String?, okRes: Int?) {
        Handler(Looper.getMainLooper()).post {
            when {
                err != null -> Toast.makeText(app, err, Toast.LENGTH_LONG).show()
                okRes != null -> Toast.makeText(app, okRes, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
