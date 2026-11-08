package dev.nemeyes.ncarousel.ui.library

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import dev.nemeyes.ncarousel.data.SsoNextcloudDavAccess
import okio.Buffer

/**
 * Coil model for Nextcloud `/core/preview` via Files SSO (reused [com.nextcloud.android.sso.api.NextcloudAPI]).
 * Prefer this over per-cell `produceState` + short-lived API clients.
 */
data class SsoPreviewData(
    val ssoAccountName: String,
    val davUserId: String,
    val fileId: Long,
    val sizePx: Int,
)

class SsoPreviewKeyer : Keyer<SsoPreviewData> {
    override fun key(data: SsoPreviewData, options: Options): String =
        "sso-preview:${data.ssoAccountName}:${data.fileId}:${data.sizePx}"
}

class SsoPreviewFetcher(
    private val context: Context,
    private val data: SsoPreviewData,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = SsoNextcloudDavAccess(
            context.applicationContext,
            data.ssoAccountName,
            data.davUserId,
        ).downloadPreview(data.fileId, data.sizePx).getOrElse { throw it }
        val buffer = Buffer().write(bytes)
        return SourceResult(
            source = ImageSource(buffer, context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<SsoPreviewData> {
        override fun create(
            data: SsoPreviewData,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = SsoPreviewFetcher(options.context, data)
    }
}
