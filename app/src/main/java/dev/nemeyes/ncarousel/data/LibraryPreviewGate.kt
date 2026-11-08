package dev.nemeyes.ncarousel.data

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Caps concurrent Nextcloud `/index.php/core/preview` fetches from the Library.
 *
 * Opening Library after a large sync composes many visible Lazy list/grid cells at once.
 * Without a gate, Coil (Basic Auth) and SSO [SsoNextcloudDavAccess.downloadPreview] fire dozens
 * of parallel preview GETs and can OOM / knock out php-fpm. Listing (PROPFIND) is separate and
 * already serial; this only throttles thumbnail traffic.
 */
object LibraryPreviewGate {
    /** Aggressive cap: 1–2 in-flight previews app-wide. */
    const val MAX_CONCURRENT: Int = 2

    private val semaphore = Semaphore(MAX_CONCURRENT)

    suspend fun <T> withPermit(block: suspend () -> T): T = semaphore.withPermit { block() }
}
