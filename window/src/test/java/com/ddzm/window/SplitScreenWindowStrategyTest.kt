package com.ddzm.window

import com.ddzm.core.Capability
import com.ddzm.core.CapabilitySnapshot
import com.ddzm.core.contract.ShellExecutor
import com.ddzm.core.window.WindowHost
import com.ddzm.core.window.WindowLevel
import com.ddzm.core.window.WindowRequest
import com.ddzm.core.window.WindowResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeShell(
    private val responses: Map<String, ShellExecutor.ShellResult> = emptyMap()
) : ShellExecutor {
    val executed = mutableListOf<String>()
    override fun available(): Boolean = true
    override fun execute(command: String): ShellExecutor.ShellResult {
        executed += command
        return responses[command] ?: ShellExecutor.ShellResult(0, "ok")
    }
}

private class FakeHost : WindowHost {
    val started = mutableListOf<String>()
    override fun startApp(packageName: String, activityName: String?): Boolean {
        started += packageName
        return true
    }

    override fun showInCard(cardId: String): Boolean = true
    override fun canDrawOverlays(): Boolean = false
    override fun hasSplitScreenSupport(): Boolean = true
}

class SplitScreenWindowStrategyTest {

    private val startCommand = "am start --windowingMode 3 -n com.example/.Map"
    private val request = WindowRequest(
        targetPackage = "com.autonavi.minimap",
        cardId = "nav",
        preferredLevel = WindowLevel.L2_SPLIT_SCREEN
    )
    private val shellCapability =
        CapabilitySnapshot(granted = setOf(Capability.SHELL_SPLIT_SCREEN))

    @Test
    fun commandExitZeroWithoutRealSplitForcesFullscreenFallback() {
        val shell = FakeShell(
            mapOf(
                startCommand to ShellExecutor.ShellResult(0, "Starting: Intent"),
                SPLIT_SCREEN_VERIFY_COMMAND to
                    ShellExecutor.ShellResult(0, "splitScreenState:0 bounds(0,0-1200,2652)")
            )
        )
        val strategy = SplitScreenWindowStrategy(
            shell, SplitScreenCommand { startCommand }, SplitScreenVerifier.NEVER
        )

        assertTrue(strategy.canHandle(request, shellCapability))
        val result = strategy.launch(request, FakeHost())
        assertTrue(result is WindowResult.Failed)
        assertTrue((result as WindowResult.Failed).reason.contains("NOT verified"))
    }

    @Test
    fun unverifiedSplitStartsTargetFullScreenAndRecordsBothCommands() {
        val shell = FakeShell(
            mapOf(
                startCommand to ShellExecutor.ShellResult(0, "Starting"),
                SPLIT_SCREEN_VERIFY_COMMAND to
                    ShellExecutor.ShellResult(0, "splitScreenState:0")
            )
        )
        val host = FakeHost()
        val strategy = SplitScreenWindowStrategy(
            shell, SplitScreenCommand { startCommand }, SplitScreenVerifier.NEVER
        )

        val result = strategy.launch(request, host)

        assertTrue(result is WindowResult.Failed)
        assertEquals(listOf("com.autonavi.minimap"), host.started)
        assertEquals(
            listOf(startCommand, SPLIT_SCREEN_VERIFY_COMMAND),
            shell.executed
        )
    }

    @Test
    fun verifiedSplitIsHandledWithoutFullscreenFallback() {
        val shell = FakeShell(
            mapOf(
                startCommand to ShellExecutor.ShellResult(0, "Starting"),
                SPLIT_SCREEN_VERIFY_COMMAND to
                    ShellExecutor.ShellResult(0, "splitScreenState:1")
            )
        )
        val host = FakeHost()
        val strategy = SplitScreenWindowStrategy(
            shell,
            SplitScreenCommand { startCommand },
            DumpsysSplitScreenVerifier()
        )

        val result = strategy.launch(request, host)

        assertTrue(result is WindowResult.Handled)
        assertEquals(WindowLevel.L2_SPLIT_SCREEN, (result as WindowResult.Handled).level)
        assertTrue(host.started.isEmpty())
    }

    @Test
    fun shellFailureStopsImmediately() {
        val shell = FakeShell(
            mapOf(startCommand to ShellExecutor.ShellResult(1, "denied"))
        )
        val host = FakeHost()
        val strategy = SplitScreenWindowStrategy(
            shell, SplitScreenCommand { startCommand }, DumpsysSplitScreenVerifier()
        )

        val result = strategy.launch(request, host)

        assertTrue(result is WindowResult.Failed)
        assertTrue(host.started.isEmpty())
        assertEquals(listOf(startCommand), shell.executed)
    }

    @Test
    fun dumpsysVerifierIsConservative() {
        val verifier = DumpsysSplitScreenVerifier()
        assertFalse(verifier.verified(""))
        assertFalse(verifier.verified("no split information at all"))
        assertFalse(verifier.verified("splitScreenState:0\nsplitScreenState:0"))
        assertTrue(verifier.verified("splitScreenState:1"))
        assertTrue(verifier.verified("mSplitScreenState = 2"))
    }

    @Test
    fun inCardFallbackStillStartsTargetApp() {
        val host = FakeHost()
        val result = InCardWindowStrategy().launch(
            request.copy(preferredLevel = WindowLevel.L0_IN_CARD),
            host
        )

        assertTrue(result is WindowResult.Handled)
        assertEquals(WindowLevel.L0_IN_CARD, (result as WindowResult.Handled).level)
        assertEquals(listOf("com.autonavi.minimap"), host.started)
    }

    @Test
    fun overlayStrategyRefusesWhenPermissionMissing() {
        val result = OverlayWindowStrategy().launch(request, FakeHost())
        assertTrue(result is WindowResult.Failed)
    }
}
