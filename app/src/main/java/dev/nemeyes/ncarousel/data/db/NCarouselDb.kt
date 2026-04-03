package dev.nemeyes.ncarousel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ImageEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NCarouselDb : RoomDatabase() {
    abstract fun imageEntryDao(): ImageEntryDao

    companion object {
        @Volatile private var INSTANCE: NCarouselDb? = null

        fun get(context: Context): NCarouselDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NCarouselDb::class.java,
                    "ncarousel.db",
                ).build().also { INSTANCE = it }
            }
    }
}

