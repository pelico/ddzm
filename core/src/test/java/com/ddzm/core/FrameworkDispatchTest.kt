package com.ddzm.core

import com.ddzm.core.window.WindowHost
import com.ddzm.core.window.WindowLevel
import com.ddzm.core.window.WindowRequest
import com.ddzm.core.window.WindowResult
import com.ddzm.core.window.WindowStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHost : WindowHost {
    val started = mutableListOf<String>()
    override fun startApp(packageName: String, activityName: String?): Boolean {
        started += packageName
        return true
    }

    override fun showInCard(cardId: String): Boolean = true
    override fun canDrawOverlays(): Boolean = false
    override fun hasSplitScreenSupport(): Boolean = false
}

class FrameworkDispatchTest {

    private fun strategy(
        id: String,
        level: WindowLevel,
        result: WindowResult
    ): WindowStrategy = object : WindowStrategy {
        override val id: String = id
        override val level: WindowLevel = level
        override val requires: Set<Capability> = emptySet()
        override fun canHandle(request: WindowRequest, capabilities: CapabilitySnapshot) = true
        override fun launch(request: WindowRequest, host: WindowHost): WindowResult = result
    }

    @Test
    fun fallsBackFromL2ToL0AndRecordsChain() {
        val scope = Framework.newScope(CapabilitySnapshot.EMPTY)
        scope.windows.register(
            strategy("split", WindowLevel.L2_SPLIT_SCREEN, WindowResult.Failed("no shell bridge"))
        )
        scope.windows.register(
            strategy(
                "in-card", WindowLevel.L0_IN_CARD,
                WindowResult.Handled(WindowLevel.L0_IN_CARD, "in-card")
            )
        )

        val result = Framework.dispatch(
            scope,
            WindowRequest(cardId = "music", preferredLevel = WindowLevel.L2_SPLIT_SCREEN),
            FakeHost()
        )

        assertTrue(result is WindowResult.Degraded)
        val degraded = result as WindowResult.Degraded
        assertEquals(WindowLevel.L0_IN_CARD, degraded.finalLevel)
        assertEquals(listOf("split@L2_SPLIT_SCREEN", "in-card@L0_IN_CARD"), degraded.attempted)
    }

    @Test
    fun returnsFailedWhenNoStrategyAvailable() {
        val scope = Framework.newScope(CapabilitySnapshot.EMPTY)
        val result = Framework.dispatch(
            scope,
            WindowRequest(cardId = "music"),
            FakeHost()
        )
        assertTrue(result is WindowResult.Failed)
        assertTrue((result as WindowResult.Failed).reason.contains("none"))
    }

    @Test
    fun firstSuccessfulLevelIsNotMarkedDegraded() {
        val scope = Framework.newScope(CapabilitySnapshot.EMPTY)
        scope.windows.register(
            strategy(
                "in-card", WindowLevel.L0_IN_CARD,
                WindowResult.Handled(WindowLevel.L0_IN_CARD, "in-card")
            )
        )
        val result = Framework.dispatch(scope, WindowRequest(cardId = "clock"), FakeHost())
        assertTrue(result is WindowResult.Handled)
    }
}
