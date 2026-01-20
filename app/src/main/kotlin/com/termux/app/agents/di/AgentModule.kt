package com.termux.app.agents.di

import android.content.Context
import com.termux.app.agents.cli.CliBridge
import com.termux.app.agents.daemon.AgentDaemon
import com.termux.app.agents.daemon.AgentRegistry
import com.termux.app.agents.runtime.AgentMemoryFactory
import com.termux.app.agents.runtime.AgentSandboxFactory
import com.termux.app.agents.runtime.CommandRunner
import com.termux.app.agents.runtime.SkillExecutor
import com.termux.app.agents.swarm.SwarmCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Hilt dependency injection module for agent components.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {
    
    private const val AGENTS_BASE_DIR = "/data/data/com.termux/files/usr/share/termux-agents"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    
    @Provides
    @Singleton
    fun provideCommandRunner(): CommandRunner {
        return CommandRunner(
            prefix = TERMUX_PREFIX,
            homeDir = TERMUX_HOME
        )
    }
    
    @Provides
    @Singleton
    fun provideAgentMemoryFactory(): AgentMemoryFactory {
        return AgentMemoryFactory(
            memoryDir = File("$AGENTS_BASE_DIR/memory")
        )
    }
    
    @Provides
    @Singleton
    fun provideAgentSandboxFactory(): AgentSandboxFactory {
        return AgentSandboxFactory(
            sandboxesDir = File("$AGENTS_BASE_DIR/sandboxes")
        )
    }
    
    @Provides
    @Singleton
    fun provideSwarmCoordinator(): SwarmCoordinator {
        return SwarmCoordinator(
            swarmDir = File("$AGENTS_BASE_DIR/swarm")
        )
    }
    
    @Provides
    @Singleton
    fun provideAgentRegistry(
        @ApplicationContext context: Context
    ): AgentRegistry {
        return AgentRegistry(context)
    }
    
    @Provides
    @Singleton
    fun provideSkillExecutor(
        commandRunner: CommandRunner,
        swarmCoordinator: SwarmCoordinator,
        memoryFactory: AgentMemoryFactory,
        sandboxFactory: AgentSandboxFactory
    ): SkillExecutor {
        return SkillExecutor(
            commandRunner = commandRunner,
            swarmCoordinator = swarmCoordinator,
            memoryFactory = memoryFactory,
            sandboxFactory = sandboxFactory
        )
    }
    
    @Provides
    @Singleton
    fun provideAgentDaemon(
        @ApplicationContext context: Context,
        registry: AgentRegistry,
        skillExecutor: SkillExecutor,
        swarmCoordinator: SwarmCoordinator
    ): AgentDaemon {
        return AgentDaemon(
            context = context,
            registry = registry,
            skillExecutor = skillExecutor,
            swarmCoordinator = swarmCoordinator
        )
    }
    
    @Provides
    @Singleton
    fun provideCliBridge(
        @ApplicationContext context: Context,
        agentDaemon: AgentDaemon
    ): CliBridge {
        return CliBridge(
            context = context,
            agentDaemon = agentDaemon
        )
    }
}
