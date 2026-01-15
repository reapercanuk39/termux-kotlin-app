package com.termux.app.di

import android.content.Context
import com.termux.app.core.deviceapi.actions.BatteryAction
import com.termux.app.core.deviceapi.service.DeviceApiService
import com.termux.app.core.logging.TermuxLogger
import com.termux.app.pkg.cli.commands.device.DeviceCommands
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Device API dependencies.
 * 
 * Provides all device API actions and related services for dependency injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object DeviceApiModule {
    
    // ========== API Actions ==========
    
    @Provides
    @Singleton
    fun provideBatteryAction(
        @ApplicationContext context: Context,
        logger: TermuxLogger
    ): BatteryAction {
        return BatteryAction(context, logger)
    }
    
    // ========== CLI Commands ==========
    
    @Provides
    @Singleton
    fun provideDeviceCommands(
        batteryAction: BatteryAction,
        logger: TermuxLogger
    ): DeviceCommands {
        return DeviceCommands(batteryAction, logger)
    }
    
    // ========== Future Actions ==========
    // Add more @Provides methods as actions are implemented:
    //
    // @Provides
    // @Singleton
    // fun provideClipboardAction(
    //     @ApplicationContext context: Context,
    //     logger: TermuxLogger
    // ): ClipboardAction {
    //     return ClipboardAction(context, logger)
    // }
    //
    // @Provides
    // @Singleton
    // fun provideLocationAction(
    //     @ApplicationContext context: Context,
    //     logger: TermuxLogger,
    //     permissionManager: PermissionManager
    // ): LocationAction {
    //     return LocationAction(context, logger, permissionManager)
    // }
}
