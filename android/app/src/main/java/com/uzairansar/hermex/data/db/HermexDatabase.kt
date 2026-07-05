package com.uzairansar.hermex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedSessionEntity::class, CachedMessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HermexDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        fun create(context: Context): HermexDatabase = Room.databaseBuilder(
            context,
            HermexDatabase::class.java,
            "hermex.db",
        ).build()
    }
}
