package com.androidengineers.agent_quickstart_android.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        JournalEntryEntity::class,
        JournalTagEntity::class,
        JournalEntryTagCrossRef::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BetterSaidDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        private const val DATABASE_NAME = "better_said.db"

        @Volatile
        private var instance: BetterSaidDatabase? = null

        fun getInstance(context: Context): BetterSaidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BetterSaidDatabase::class.java,
                    DATABASE_NAME,
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
