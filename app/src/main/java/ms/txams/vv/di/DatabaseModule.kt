package ms.txams.vv.di

import android.content.Context
import androidx.room.Room
import ms.txams.vv.data.database.MusicDatabase
import ms.txams.vv.data.database.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "music_database"
        ).build()
    }

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao {
        return database.songDao()
    }
}
