package dev.nemeyes.ncarousel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageEntryDao {
    @Query("SELECT href FROM image_entries WHERE accountId = :accountId AND isCollection = 0 ORDER BY href ASC")
    suspend fun listImageHrefs(accountId: String): List<String>

    @Query("SELECT COUNT(*) FROM image_entries WHERE accountId = :accountId AND isCollection = 0")
    fun observeImageCount(accountId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ImageEntryEntity>)

    @Query("DELETE FROM image_entries WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)
}

