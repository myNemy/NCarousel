package dev.nemeyes.ncarousel.data

import android.os.SystemClock
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caps Nextcloud `/index.php/core/preview` traffic from the Library.
 *
 * Opening Library after a large sync (thousands of `oc:fileid`s) can compose many Lazy
 * cells. Each `/core/preview` hit runs PHP image decode/scale; unbounded parallel GETs
 * knock out php-fpm. Listing (Depth:1 PROPFIND) is separate and already serial.
 *
 * This gate allows a **modest** parallel burst (not a hard single-flight) plus a short
 * spacing between starts. Combined with visible-only cell loads and a reused SSO
 * [com.nextcloud.android.sso.api.NextcloudAPI], thumbs can stay **on by default** without
 * the storm that followed the SSO produceState-per-cell path.
 *
 * Uses a JDK [Semaphore] so OkHttp network interceptors (Basic Auth Coil) and coroutines
 * (SSO Coil fetcher) share the same lock without gating Coil memory/disk cache hits.
 */
object LibraryPreviewGate {
    /** Modest parallel preview fetches app-wide (Basic Auth OkHttp + SSO). */
    const val MAX_CONCURRENT: Int = 3

    /**
     * Small pause between starting consecutive preview fetches so php-fpm / Imagick can
     * recycle between bursts. Not a 750ms single-flight workaround.
     */
    const val MIN_INTERVAL_MS: Long = 120L

    private val permits = Semaphore(MAX_CONCURRENT, true)
    private val spacingLock = Any()
    @Volatile
    private var lastStartElapsedMs: Long = 0L

    /** SSO / coroutine callers: acquire off the main thread, then run [block]. */
    suspend fun <T> withPermit(block: suspend () -> T): T {
        withContext(Dispatchers.IO) {
            acquireBlocking()
        }
        try {
            return block()
        } finally {
            permits.release()
        }
    }

    /** OkHttp network interceptor (blocking thread pool) / SSO fetch under an account lock. */
    fun <T> withPermitBlocking(block: () -> T): T {
        acquireBlocking()
        try {
            return block()
        } finally {
            permits.release()
        }
    }

    private fun acquireBlocking() {
        permits.acquire()
        try {
            synchronized(spacingLock) {
                val now = SystemClock.elapsedRealtime()
                val wait = lastStartElapsedMs + MIN_INTERVAL_MS - now
                if (wait > 0L) {
                    Thread.sleep(wait)
                }
                lastStartElapsedMs = SystemClock.elapsedRealtime()
            }
        } catch (t: Throwable) {
            permits.release()
            throw t
        }
    }
}
