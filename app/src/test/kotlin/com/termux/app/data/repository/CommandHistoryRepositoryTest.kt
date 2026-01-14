package com.termux.app.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandHistoryRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var historyRepository: CommandHistoryRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        historyRepository = CommandHistoryRepository(testScope, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addCommand adds command to history`() = runTest {
        historyRepository.addCommand("ls -la")
        advanceUntilIdle()

        historyRepository.history.test {
            val history = awaitItem()
            assertEquals("Should have one command", 1, history.size)
            assertEquals("Command should match", "ls -la", history.first().command)
        }
    }

    @Test
    fun `addCommand trims whitespace`() = runTest {
        historyRepository.addCommand("  pwd  ")
        advanceUntilIdle()

        historyRepository.history.test {
            val history = awaitItem()
            assertEquals("Command should be trimmed", "pwd", history.first().command)
        }
    }

    @Test
    fun `addCommand ignores blank commands`() = runTest {
        historyRepository.addCommand("   ")
        advanceUntilIdle()

        historyRepository.history.test {
            val history = awaitItem()
            assertTrue("History should be empty", history.isEmpty())
        }
    }

    @Test
    fun `recentCommands returns last 50 commands`() = runTest {
        // Add 60 commands
        repeat(60) { i ->
            historyRepository.addCommand("command $i")
        }
        advanceUntilIdle()

        historyRepository.recentCommands.test {
            val recent = awaitItem()
            assertEquals("Should have 50 recent commands", 50, recent.size)
        }
    }

    @Test
    fun `searchHistory filters by query`() = runTest {
        historyRepository.addCommand("git status")
        historyRepository.addCommand("git commit")
        historyRepository.addCommand("ls -la")
        advanceUntilIdle()

        historyRepository.searchHistory("git").test {
            val results = awaitItem()
            assertEquals("Should find 2 git commands", 2, results.size)
            assertTrue("All results should contain 'git'", results.all { it.command.contains("git") })
        }
    }

    @Test
    fun `getCommandsWithPrefix returns matching commands`() = runTest {
        historyRepository.addCommand("git status")
        historyRepository.addCommand("git commit")
        historyRepository.addCommand("go build")
        advanceUntilIdle()

        historyRepository.getCommandsWithPrefix("git").test {
            val results = awaitItem()
            assertEquals("Should find 2 commands starting with 'git'", 2, results.size)
        }
    }

    @Test
    fun `deleteCommand removes command from history`() = runTest {
        historyRepository.addCommand("test command")
        advanceUntilIdle()

        val commandId = historyRepository.history.value.first().id
        historyRepository.deleteCommand(commandId)
        advanceUntilIdle()

        historyRepository.history.test {
            val history = awaitItem()
            assertTrue("History should be empty after delete", history.isEmpty())
        }
    }

    @Test
    fun `clearHistory removes all commands`() = runTest {
        repeat(5) { i ->
            historyRepository.addCommand("command $i")
        }
        advanceUntilIdle()

        historyRepository.clearHistory()
        advanceUntilIdle()

        historyRepository.history.test {
            val history = awaitItem()
            assertTrue("History should be empty", history.isEmpty())
        }
    }

    @Test
    fun `getStatistics returns correct counts`() = runTest {
        historyRepository.addCommand("git status", exitCode = 0)
        historyRepository.addCommand("git commit", exitCode = 0)
        historyRepository.addCommand("invalid_command", exitCode = 1)
        advanceUntilIdle()

        historyRepository.getStatistics().test {
            val stats = awaitItem()
            assertEquals("Total commands should be 3", 3, stats.totalCommands)
            assertEquals("Unique commands should be 3", 3, stats.uniqueCommands)
            assertEquals("Successful commands should be 2", 2, stats.successfulCommands)
            assertEquals("Failed commands should be 1", 1, stats.failedCommands)
        }
    }
}
