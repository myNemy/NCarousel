package dev.nemeyes.ncarousel.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens an https URL in a Custom Tab when available, otherwise the default browser.
 * Login Flow v2 needs a working browser; missing Custom Tabs must not silently fail.
 */
object ExternalBrowser {
    fun open(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        return try {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            openWithViewIntent(context, uri)
        } catch (_: Exception) {
            openWithViewIntent(context, uri)
        }
    }

    private fun openWithViewIntent(context: Context, uri: Uri): Boolean =
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
