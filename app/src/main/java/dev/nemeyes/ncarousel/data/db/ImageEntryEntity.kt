package dev.nemeyes.ncarousel.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image_entries",
    indices = [
        Index(value = ["accountId", "href"], unique = true),
    ],
)
data class ImageEntryEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val accountId: String,
    /** Full href path from WebDAV responses (decoded). */
    val href: String,
    val contentType: String?,
    val contentLengthBytes: Long?,
    val etag: String?,
    val lastModified: String?,
    val isCollection: Boolean,
    val scannedAtEpochMs: Long,
)

