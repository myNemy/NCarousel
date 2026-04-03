package dev.nemeyes.ncarousel.data.accounts

import java.util.UUID

data class NextcloudAccount(
    val id: String = UUID.randomUUID().toString(),
    val serverBaseUrl: String,
    /** UID used for WebDAV path: `/remote.php/dav/files/{userId}/...` */
    val userId: String,
    /** Login identifier returned by login flow (can differ from UID, e.g. email). */
    val loginName: String,
    val appPassword: String,
    val remoteFolder: String = "Photos",
)

