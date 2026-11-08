package dev.nemeyes.ncarousel.ui.library

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import dev.nemeyes.ncarousel.data.LibraryPreviewGate
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * Coil [ImageLoader] for Library Basic-Auth preview URLs.
 *
 * Uses a dedicated OkHttp client with [LibraryPreviewGate.MAX_CONCURRENT] max requests so
 * Lazy grid/list composition cannot open a large parallel preview storm against Nextcloud.
 */
object LibraryPreviewImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(appContext: Context): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = LibraryPreviewGate.MAX_CONCURRENT
            maxRequestsPerHost = LibraryPreviewGate.MAX_CONCURRENT
        }
        val okHttp = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        return ImageLoader.Builder(appContext)
            .okHttpClient(okHttp)
            // Memory cache still helps scroll-back; network stays gated by OkHttp dispatcher.
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
