package com.termux.app.data.repository

import app.cash.turbine.test
import com.termux.app.data.model.TerminalLayout
import io.mockk.coEvery
import io.mockk.mockk
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalSessionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var sessionManager: TerminalSessionManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionManager = TerminalSessionManager(testScope, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial session is created on startup`() = runTest {
        advanceUntilIdle()

        sessionManager.sessions.test {
            val sessions = awaitItem()
            assertTrue("Should have at least one session", sessions.isNotEmpty())
        }
    }

    @Test
    fun `createSession adds new session`() = runTest {
        advanceUntilIdle()

        val initialCount = sessionManager.sessions.value.size
        val newSessionId = sessionManager.createSession("Test Session")

        val sessions = sessionManager.sessions.value
        assertEquals("Should have one more session", initialCount + 1, sessions.size)
        assertNotNull("New session should exist", sessions.find { it.id == newSessionId })
    }

    @Test
    fun `closeSession removes session`() = runTest {
        advanceUntilIdle()

        val sessionId = sessionManager.createSession("Test Session")
        val countAfterCreate = sessionManager.sessions.value.size

        sessionManager.closeSession(sessionId)
        val countAfterClose = sessionManager.sessions.value.size

        assertEquals("Should have one less session", countAfterCreate - 1, countAfterClose)
    }

    @Test
    fun `setActiveSession updates active session`() = runTest {
        advanceUntilIdle()

        val session1 = sessionManager.createSession("Session 1")
        val session2 = sessionManager.createSession("Session 2")

        sessionManager.setActiveSession(session1)
        assertEquals("Active session should be session1", session1, sessionManager.activeSessionId.value)

        sessionManager.setActiveSession(session2)
        assertEquals("Active session should be session2", session2, sessionManager.activeSessionId.value)
    }

    @Test
    fun `updateSessionTitle changes session title`() = runTest {
        advanceUntilIdle()

        val sessionId = sessionManager.createSession("Original Title")

        sessionManager.updateSessionTitle(sessionId, "New Title")
        val session = sessionManager.getSession(sessionId)

        assertEquals("Title should be updated", "New Title", session?.title)
    }

    @Test
    fun `splitHorizontal creates horizontal layout`() = runTest {
        advanceUntilIdle()

        sessionManager.splitHorizontal()

        val layout = sessionManager.layout.value
        assertTrue("Layout should be HorizontalSplit", layout is TerminalLayout.HorizontalSplit)
    }

    @Test
    fun `splitVertical creates vertical layout`() = runTest {
        advanceUntilIdle()

        sessionManager.splitVertical()

        val layout = sessionManager.layout.value
        assertTrue("Layout should be VerticalSplit", layout is TerminalLayout.VerticalSplit)
    }

    @Test
    fun `closeSplit returns to single layout`() = runTest {
        advanceUntilIdle()

        sessionManager.splitHorizontal()
        sessionManager.closeSplit()

        val layout = sessionManager.layout.value
        assertTrue("Layout should be Single", layout is TerminalLayout.Single)
    }

    @Test
    fun `adjustSplitRatio clamps value between 0_2 and 0_8`() = runTest {
        advanceUntilIdle()

        sessionManager.splitHorizontal()

        // Test lower bound
        sessionManager.adjustSplitRatio(0.1f)
        var layout = sessionManager.layout.value as TerminalLayout.HorizontalSplit
        assertEquals("Ratio should be clamped to 0.2", 0.2f, layout.splitRatio)

        // Test upper bound
        sessionManager.adjustSplitRatio(0.9f)
        layout = sessionManager.layout.value as TerminalLayout.HorizontalSplit
        assertEquals("Ratio should be clamped to 0.8", 0.8f, layout.splitRatio)

        // Test valid value
        sessionManager.adjustSplitRatio(0.6f)
        layout = sessionManager.layout.value as TerminalLayout.HorizontalSplit
        assertEquals("Ratio should be 0.6", 0.6f, layout.splitRatio)
    }
}
