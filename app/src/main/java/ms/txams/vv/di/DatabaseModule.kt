package ms.txams.vv.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ms.txams.vv.core.data.database.TXADatabase
import ms.txams.vv.core.data.database.dao.TXAPlaylistDao
import ms.txams.vv.core.data.database.dao.TXAQueueDao
import ms.txams.vv.core.data.database.dao.TXASongDao
import javax.inject.Singleton

/**
 * Hilt Database Module - Cung cấp Room database và DAOs
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTXADatabase(@ApplicationContext context: Context): TXADatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            TXADatabase::class.java,
            TXADatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration() // For development only
        .build()
    }

    @Provides
    fun provideSongDao(database: TXADatabase): TXASongDao {
        return database.songDao()
    }

    @Provides
    fun providePlaylistDao(database: TXADatabase): TXAPlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideQueueDao(database: TXADatabase): TXAQueueDao {
        return database.queueDao()
    }
}
