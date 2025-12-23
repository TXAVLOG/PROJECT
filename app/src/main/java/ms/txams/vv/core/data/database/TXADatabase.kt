package ms.txams.vv.core.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import ms.txams.vv.core.data.database.entity.TXASongEntity
import ms.txams.vv.core.data.database.entity.TXAPlaylistEntity
import ms.txams.vv.core.data.database.entity.TXAPlaylistSongEntity
import ms.txams.vv.core.data.database.entity.TXAQueueEntity
import ms.txams.vv.core.data.database.entity.TXAQueueHistoryEntity
import ms.txams.vv.core.data.database.dao.TXASongDao
import ms.txams.vv.core.data.database.dao.TXAPlaylistDao
import ms.txams.vv.core.data.database.dao.TXAQueueDao
import ms.txams.vv.core.data.database.converters.TXADatabaseConverters

/**
 * Room Database for TXA Music Player
 * Version 1 - Initial release with basic entities
 */
@Database(
    entities = [
        TXASongEntity::class,
        TXAPlaylistEntity::class,
        TXAPlaylistSongEntity::class,
        TXAQueueEntity::class,
        TXAQueueHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(TXADatabaseConverters::class)
abstract class TXADatabase : RoomDatabase() {

    abstract fun songDao(): TXASongDao
    abstract fun playlistDao(): TXAPlaylistDao
    abstract fun queueDao(): TXAQueueDao

    companion object {
        const val DATABASE_NAME = "txa_music_database"

        // Singleton instance
        @Volatile
        private var INSTANCE: TXADatabase? = null

        fun getDatabase(context: Context): TXADatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TXADatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(*getAllMigrations())
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration() // For development only
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getAllMigrations(): Array<Migration> {
            return arrayOf(
                // Future migrations will be added here
            )
        }
    }

    /**
     * Database callback for initial data population
     */
    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            
            // Create initial system playlists
            db.execSQL("""
                INSERT INTO playlists (name, description, is_system, created_at, modified_at) 
                VALUES ('Favorites', 'Your favorite songs', 1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})
            """.trimIndent())
            
            db.execSQL("""
                INSERT INTO playlists (name, description, is_system, created_at, modified_at) 
                VALUES ('Recently Played', 'Recently played songs', 1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})
            """.trimIndent())
            
            db.execSQL("""
                INSERT INTO playlists (name, description, is_system, created_at, modified_at) 
                VALUES ('Most Played', 'Your most played songs', 1, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})
            """.trimIndent())
        }
    }
}
