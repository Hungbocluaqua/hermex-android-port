package com.uzairansar.hermex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedSessionEntity::class, CachedMessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        fun create(context: Context): HermexDatabase = Room.databaseBuilder(
            context,
            HermexDatabase::class.java,
            "hermex.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN isCliSession INTEGER")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN sourceTag TEXT")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN rawSource TEXT")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN sessionSource TEXT")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN sourceLabel TEXT")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN parentSessionId TEXT")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN relationshipType TEXT")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN readOnly INTEGER")
                db.execSQL("ALTER TABLE cached_sessions ADD COLUMN isReadOnly INTEGER")
            }
        }
    }
}
