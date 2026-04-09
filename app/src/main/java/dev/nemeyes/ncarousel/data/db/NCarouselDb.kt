package dev.nemeyes.ncarousel.data.db

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ImageEntryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NCarouselDb : RoomDatabase() {
    abstract fun imageEntryDao(): ImageEntryDao

    companion object {
        @Volatile private var INSTANCE: NCarouselDb? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE image_entries ADD COLUMN fileId INTEGER")
                }
            }

        fun get(context: Context): NCarouselDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NCarouselDb::class.java,
                    "ncarousel.db",
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}

