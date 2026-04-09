package dev.nemeyes.ncarousel.data

import android.content.Context
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccount
import dev.nemeyes.ncarousel.data.db.ImageEntryEntity
import dev.nemeyes.ncarousel.data.db.NCarouselDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline-first list sync:
 * - fetch from WebDAV
 * - persist list in Room per-account
 * - provide cached href list when offline
 */
class ImageSyncRepository(context: Context) {
    private val app = context.applicationContext
    private val dao = NCarouselDb.get(app).imageEntryDao()

    suspend fun readCachedHrefs(accountId: String): List<String> =
        withContext(Dispatchers.IO) { dao.listImageHrefs(accountId) }

    suspend fun readCachedHrefsWithFileId(accountId: String): List<Pair<String, Long?>> =
        withContext(Dispatchers.IO) {
            dao.listImageHrefsWithFileId(accountId).map { it.href to it.fileId }
        }

    suspend fun syncFromServer(
        http: okhttp3.OkHttpClient,
        account: NextcloudAccount,
        maxImageBytes: Long,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val client = NextcloudWebDavClient(
                http,
                account.serverBaseUrl,
                account.userId,
                account.loginName,
                account.appPassword,
            )
            val items = client.listImageEntriesRecursive(account.remoteFolder, maxImageBytes).getOrThrow()
            val now = System.currentTimeMillis()
            val entries = items.map { e ->
                ImageEntryEntity(
                    accountId = account.id,
                    href = e.hrefDecoded,
                    fileId = e.fileId,
                    contentType = e.contentType,
                    contentLengthBytes = e.contentLengthBytes,
                    etag = e.etag,
                    lastModified = e.lastModified,
                    isCollection = false,
                    scannedAtEpochMs = now,
                )
            }
            dao.deleteAllForAccount(account.id)
            dao.upsertAll(entries)
            entries.map { it.href }
        }
    }
}

