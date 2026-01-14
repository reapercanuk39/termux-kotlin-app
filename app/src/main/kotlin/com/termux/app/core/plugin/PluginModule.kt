package com.termux.app.core.plugin

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for plugin-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {
    
    @Binds
    @Singleton
    abstract fun bindPluginRegistry(impl: PluginRegistryImpl): PluginRegistry
    
    @Binds
    @Singleton
    abstract fun bindPluginHostFactory(impl: PluginHostFactoryImpl): PluginHostFactory
}
