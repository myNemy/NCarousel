package dev.nemeyes.ncarousel.data

import android.os.SystemClock
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Caps Nextcloud `/index.php/core/preview` traffic from the Library.
 *
 * Opening Library after a large sync (thousands of `oc:fileid`s) can compose many Lazy
 * cells. Each `/core/preview` hit runs PHP image decode/scale; a burst of parallel GETs
 * can exhaust php-fpm / reverse-proxy workers until the host starts refusing connections
 * (repro: ~5k library via SSO, refuse right after opening Library; listing alone was fine).
 *
 * Policy: **single-flight** network preview + spacing between starts. Combined with
 * visible-only cell loads and a short Library enter settle. Cell thumbs are **Basic Auth
 * only** by product policy (SSO-only accounts skip auto `/core/preview`; optional
 * on-demand fullscreen may still use a reused SSO NextcloudAPI).
 *
 * Uses a JDK [Semaphore] so OkHttp network interceptors (Basic Auth Coil) and coroutines
 * (SSO Coil fetcher) share the same lock without gating Coil memory/disk cache hits.
 */
object LibraryPreviewGate {
    /**
     * One in-flight `/core/preview` app-wide (Basic Auth OkHttp + SSO NextcloudAPI).
     * Single-flight protects php-fpm; spacing is the secondary brake (not token Basic Auth).
     */
    const val MAX_CONCURRENT: Int = 1

    /**
     * Pause between starting consecutive preview fetches so php-fpm / Imagick can
     * finish one decode before the next starts.
     */
    const val MIN_INTERVAL_MS: Long = 1000L

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
