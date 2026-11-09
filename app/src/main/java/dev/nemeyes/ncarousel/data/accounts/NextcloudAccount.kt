package dev.nemeyes.ncarousel.data.accounts

import java.util.UUID

data class NextcloudAccount(
    val id: String = UUID.randomUUID().toString(),
    val serverBaseUrl: String,
    /** UID used for WebDAV path: `/remote.php/dav/files/{userId}/...` */
    val userId: String,
    /** Login identifier returned by login flow (can differ from UID, e.g. email). */
    val loginName: String,
    /**
     * App password for direct OkHttp Basic Auth (Library cell thumbs, WebDAV when not SSO).
     * Empty when [ssoAccountName] is set (listing/wallpaper go through Nextcloud Files SSO;
     * Library auto-thumbs stay off until Login Flow stores an app password here).
     */
    val appPassword: String,
    val remoteFolder: String = "Photos",
    /**
     * Android Account Manager name from Nextcloud Files SSO.
     * When non-null, network access must use [com.nextcloud.android.sso.api.NextcloudAPI]
     * — never use the SSO local token as OkHttp Basic Auth.
     */
    val ssoAccountName: String? = null,
) {
    val usesSso: Boolean get() = !ssoAccountName.isNullOrBlank()
}
