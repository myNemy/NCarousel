package dev.nemeyes.ncarousel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ImageHrefWithFileId(
    val href: String,
    val fileId: Long?,
)

data class ImageHrefWithMeta(
    val href: String,
    val fileId: Long?,
    val lastModified: String?,
)

@Dao
interface ImageEntryDao {
    @Query("SELECT href FROM image_entries WHERE accountId = :accountId AND isCollection = 0 ORDER BY href ASC")
    suspend fun listImageHrefs(accountId: String): List<String>

    @Query("SELECT href, fileId FROM image_entries WHERE accountId = :accountId AND isCollection = 0 ORDER BY href ASC")
    suspend fun listImageHrefsWithFileId(accountId: String): List<ImageHrefWithFileId>

    @Query(
        "SELECT href, fileId, lastModified FROM image_entries " +
            "WHERE accountId = :accountId AND isCollection = 0 ORDER BY href ASC",
    )
    suspend fun listImageHrefsWithMeta(accountId: String): List<ImageHrefWithMeta>

    @Query("SELECT COUNT(*) FROM image_entries WHERE accountId = :accountId AND isCollection = 0")
    fun observeImageCount(accountId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ImageEntryEntity>)

    @Query("DELETE FROM image_entries WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}

