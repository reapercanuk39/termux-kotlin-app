package com.termux.app.boot

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Termux:Boot components.
 */
@Module
@InstallIn(SingletonComponent::class)
object BootModule {
    
    @Provides
    @Singleton
    fun provideBootPreferences(
        @ApplicationContext context: Context
    ): BootPreferences {
        return BootPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideBootScriptExecutor(
        @ApplicationContext context: Context,
        bootPreferences: BootPreferences
    ): BootScriptExecutor {
        return BootScriptExecutor(context, bootPreferences)
    }
}
