package dev.nemeyes.ncarousel.ui.library

import android.content.Context
import com.nextcloud.android.sso.AccountImporter

/**
 * Credentials for Library thumbnails over plain OkHttp (v0.2.55 Coil path).
 *
 * Listing/sync for SSO accounts still uses [com.nextcloud.android.sso.api.NextcloudAPI].
 * Per-thumb binder calls were enough to push nginx/apache into connection refusal on
 * ~5k libraries; the SSO [token] is the app password Files already uses server-side, so
 * Basic Auth previews match the pre-SSO Coil behaviour without storing the password in
 * [dev.nemeyes.ncarousel.MainUiState].
 */
internal data class SsoPreviewAuth(
    val userId: String,
    val token: String,
) {
    companion object {
        fun load(context: Context, ssoAccountName: String): SsoPreviewAuth? =
            runCatching {
                val account = AccountImporter.getSingleSignOnAccount(
                    context.applicationContext,
                    ssoAccountName,
                )
                val userId = account.userId?.trim().orEmpty()
                val token = account.token?.trim().orEmpty()
                if (userId.isEmpty() || token.isEmpty()) null
                else SsoPreviewAuth(userId = userId, token = token)
            }.getOrNull()
    }
}
