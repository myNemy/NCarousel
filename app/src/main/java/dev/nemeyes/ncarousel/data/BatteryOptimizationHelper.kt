package dev.nemeyes.ncarousel.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Su API 23+ le ottimizzazioni batteria (Doze / app standby) possono ritardare o bloccare
 * [androidx.work.WorkManager]: utile chiedere l’esclusione per il cambio sfondo affidabile.
 */
object BatteryOptimizationHelper {

    fun isExemptFromBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** True se conviene chiedere all’utente di escludere l’app (non ancora esclusa). */
    fun shouldPromptForExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return !isExemptFromBatteryOptimizations(context)
    }

    /**
     * Avvia il flusso di sistema per escludere l’app dalle ottimizzazioni batteria.
     * In caso di intent non gestito, apre la schermata elenco app.
     */
    fun startExemptionRequest(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val app = context.applicationContext
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${app.packageName}")
        }
        if (context !is Activity) {
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(request)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                }
                if (context !is Activity) appSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(appSettings) }
            }
        }
    }
}
