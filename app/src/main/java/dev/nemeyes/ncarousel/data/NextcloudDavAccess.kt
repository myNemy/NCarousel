package dev.nemeyes.ncarousel.data

/**
 * File/WebDAV operations against a Nextcloud account.
 * Implementations: OkHttp Basic Auth ([NextcloudWebDavClient]) or Files-app SSO ([SsoNextcloudDavAccess]).
 */
interface NextcloudDavAccess {
    suspend fun verifyReachable(): Result<Unit>

    /**
     * Immediate child collection paths (relative to the user’s DAV root), sorted A→Z.
     * [relativeFolder] empty = account root.
     */
    suspend fun listChildFolders(relativeFolder: String): Result<List<String>>

    suspend fun listImageHrefsRecursive(
        remoteFolder: String,
        maxImageBytes: Long = 0L,
    ): Result<List<String>>

    suspend fun listImageEntriesRecursive(
        remoteFolder: String,
        maxImageBytes: Long = 0L,
    ): Result<List<DavEntry>>

    suspend fun downloadFile(hrefPath: String): Result<ByteArray>

    suspend fun downloadRelativeFile(relativePath: String): Result<ByteArray>

    suspend fun ensureFolder(relativeFolder: String): Result<Unit>

    suspend fun putFile(
        relativePath: String,
        bytes: ByteArray,
        contentType: String = "text/plain; charset=utf-8",
    ): Result<Unit>
}
