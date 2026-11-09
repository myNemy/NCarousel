package dev.nemeyes.ncarousel.data

import android.content.Context
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object HttpClientProvider {
    /**
     * Short client id for Nextcloud Login Flow / app-password naming.
     * Official Login Flow docs: set a proper `USER_AGENT`; the grant page tells the user they
     * will grant access to that value
     * (https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html).
     */
    fun loginFlowUserAgent(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
            ?.substringBefore('+')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "dev"
        return "NCarousel/$version"
    }

    fun create(context: Context): OkHttpClient {
        val lang = Locale.getDefault().toLanguageTag()
        // Human-friendly UA for Nextcloud grant UI / app-password labels.
        // Nominatim requests add a contact URL in [NominatimReverseGeocoder].
        val ua = loginFlowUserAgent(context)
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept-Language", lang)
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(req)
            }
            .build()
    }
}
