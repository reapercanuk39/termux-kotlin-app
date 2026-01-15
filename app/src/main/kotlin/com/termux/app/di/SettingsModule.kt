package com.termux.app.di

import android.content.Context
import androidx.room.Room
import com.termux.app.pkg.backup.PackageBackupManager
import com.termux.app.pkg.doctor.PackageDoctor
import com.termux.app.ui.settings.data.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Settings UI dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    
    @Provides
    @Singleton
    fun provideProfileDatabase(
        @ApplicationContext context: Context
    ): ProfileDatabase {
        return Room.databaseBuilder(
            context,
            ProfileDatabase::class.java,
            "termux_profiles.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideProfileDao(database: ProfileDatabase): ProfileDao {
        return database.profileDao()
    }
    
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore {
        return SettingsDataStore(context)
    }
    
    @Provides
    @Singleton
    fun provideProfileRepository(
        profileDao: ProfileDao,
        settingsDataStore: SettingsDataStore
    ): ProfileRepository {
        return ProfileRepository(profileDao, settingsDataStore)
    }
}

/**
 * Hilt module for Theme repository binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeModule {
    
    @Binds
    @Singleton
    abstract fun bindThemeRepository(
        impl: ThemeRepositoryImpl
    ): ThemeRepository
}

/**
 * Hilt module for Package Management dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object PackageManagementModule {
    
    @Provides
    @Singleton
    fun providePackageBackupManager(
        @ApplicationContext context: Context
    ): PackageBackupManager {
        return PackageBackupManager(context)
    }
    
    @Provides
    @Singleton
    fun providePackageDoctor(
        @ApplicationContext context: Context
    ): PackageDoctor {
        return PackageDoctor(context)
    }
}
