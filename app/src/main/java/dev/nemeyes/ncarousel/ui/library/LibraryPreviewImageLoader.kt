package dev.nemeyes.ncarousel.ui.library

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import dev.nemeyes.ncarousel.data.LibraryPreviewGate
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Shared Coil [ImageLoader] for Library previews (Basic Auth URLs + SSO [SsoPreviewData]).
 *
 * Mirrors the v0.2.55 Coil path for Basic Auth (shared OkHttp, disk/memory cache) while
 * capping real network preview starts via [LibraryPreviewGate] (single-flight + spacing).
 * SSO uses a dedicated fetcher over a reused NextcloudAPI.
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
            // Network-only: disk/memory Coil hits do not acquire the preview gate.
            .addNetworkInterceptor(
                Interceptor { chain ->
                    LibraryPreviewGate.withPermitBlocking {
                        chain.proceed(chain.request())
                    }
                },
            )
            .build()
        return ImageLoader.Builder(appContext)
            .okHttpClient(okHttp)
            .components {
                add(SsoPreviewKeyer())
                add(SsoPreviewFetcher.Factory())
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
