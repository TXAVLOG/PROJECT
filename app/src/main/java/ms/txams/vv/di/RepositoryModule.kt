package ms.txams.vv.di

import ms.txams.vv.data.repository.MusicRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Repository is already @Singleton + @Inject, so Hilt knows how to create it.
    // Explicit binding is not strictly necessary unless we have an interface.
    // But we can provide it here if we want to abstract it later.
}
