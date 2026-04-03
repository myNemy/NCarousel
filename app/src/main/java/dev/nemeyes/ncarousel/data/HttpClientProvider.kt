package dev.nemeyes.ncarousel.data

import android.content.Context
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object HttpClientProvider {
    fun create(context: Context): OkHttpClient {
        val lang = Locale.getDefault().toLanguageTag()
        val ua = "NCarousel (Android)"
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

