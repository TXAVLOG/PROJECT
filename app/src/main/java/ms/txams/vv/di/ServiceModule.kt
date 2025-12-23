package ms.txams.vv.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ms.txams.vv.core.TXATranslation
import ms.txams.vv.core.audio.TXAAudioInjectionManager
import ms.txams.vv.core.audio.TXAAudioProcessor
import javax.inject.Singleton

/**
 * Hilt Service Module - Cung cấp audio components và translation service
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideTXATranslation(@ApplicationContext context: Context): TXATranslation {
        return TXATranslation.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideTXAAudioProcessor(@ApplicationContext context: Context): TXAAudioProcessor {
        return TXAAudioProcessor(context)
    }

    @Provides
    @Singleton
    fun provideTXAAudioInjectionManager(@ApplicationContext context: Context): TXAAudioInjectionManager {
        return TXAAudioInjectionManager(context)
    }
}
