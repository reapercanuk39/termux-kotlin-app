package com.termux.app.widget

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Termux:Widget components.
 */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {
    
    @Provides
    @Singleton
    fun provideShortcutScanner(
        @ApplicationContext context: Context
    ): ShortcutScanner {
        return ShortcutScanner(context)
    }
    
    @Provides
    @Singleton
    fun provideWidgetPreferences(
        @ApplicationContext context: Context
    ): WidgetPreferences {
        return WidgetPreferences(context)
    }
}
