package gc.txa.demo.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import gc.txa.demo.data.database.MusicDatabase
import gc.txa.demo.data.database.SongDao
import gc.txa.demo.data.repository.MusicRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            MusicDatabase::class.java,
            "music_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    @Singleton
    fun provideMusicRepository(
        songDao: SongDao,
        @ApplicationContext context: Context
    ): MusicRepository {
        return MusicRepository(songDao, context)
    }
}
