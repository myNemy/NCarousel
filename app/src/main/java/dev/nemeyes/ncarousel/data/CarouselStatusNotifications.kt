package dev.nemeyes.ncarousel.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.nemeyes.ncarousel.MainActivity
import dev.nemeyes.ncarousel.R

/**
 * Optional status notifications: library count and geographic label from the image applied as wallpaper.
 */
object CarouselStatusNotifications {

    /** Esportato per [android.provider.Settings] canale notifiche. */
    const val CHANNEL_ID = "ncarousel_status"
    private const val NOTIF_WALLPAPER_ID = 7101
    private const val NOTIF_LIST_ID = 7102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notify_channel_desc)
        }
        nm.createNotificationChannel(ch)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    fun maybeShowWallpaperApplied(
        context: Context,
        prefs: CarouselPreferences,
        progress: PickProgress,
        imageBytes: ByteArray,
    ) {
        if (!prefs.showStatusNotifications) return
        if (!prefs.notifyWallpaperApplied) return
        if (!canNotify(context)) return
        ensureChannel(context)
        val app = context.applicationContext
        val text = if (prefs.notifyWallpaperIncludeLocation) {
            val place = ImageExifPlaceLabel.fromImageBytes(app, imageBytes, prefs)
            app.getString(
                R.string.notify_wallpaper_body,
                progress.current,
                progress.total,
                place,
            )
        } else {
            app.getString(
                R.string.notify_wallpaper_body_no_place,
                progress.current,
                progress.total,
            )
        }
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ncarousel)
            .setContentTitle(app.getString(R.string.notify_wallpaper_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(app))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(app).notify(NOTIF_WALLPAPER_ID, n)
    }

    fun maybeShowListRefreshed(context: Context, prefs: CarouselPreferences, count: Int) {
        if (!prefs.showStatusNotifications) return
        if (!prefs.notifyLibraryRefreshed) return
        if (!canNotify(context)) return
        ensureChannel(context)
        val app = context.applicationContext
        val text = app.getString(R.string.notify_list_body, count)
        val n = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ncarousel)
            .setContentTitle(app.getString(R.string.notify_list_title))
            .setContentText(text)
            .setContentIntent(contentIntent(app))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(app).notify(NOTIF_LIST_ID, n)
    }
}
