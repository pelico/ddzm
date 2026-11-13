package com.ddzm.window

import com.ddzm.core.CapabilityProbe
import com.ddzm.core.LauncherPlugin
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys
import com.ddzm.core.contract.ShellExecutor

class WindowModule(
    private val probe: CapabilityProbe,
    private val shell: ShellExecutor,
    private val splitCommand: SplitScreenCommand = SplitScreenCommand { null },
    /** 只有外部确实具备 shell 身份并做完运行时验证时才注入真实 verifier。 */
    private val splitVerifier: SplitScreenVerifier = SplitScreenVerifier.NEVER
) : LauncherPlugin {

    override val id: String = "window"

    override fun install(scope: LauncherScope) {
        scope.services.put(ServiceKeys.CAPABILITY_PROBE, probe)
        scope.services.put(ServiceKeys.SHELL_EXECUTOR, shell)

        // 注册顺序无关：Framework.dispatch 按 WindowLevel 排序调用。
        scope.windows.register(InCardWindowStrategy())
        scope.windows.register(OverlayWindowStrategy())
        scope.windows.register(
            SplitScreenWindowStrategy(shell, splitCommand, splitVerifier)
        )
        scope.windows.register(EmbeddedWindowStrategy())
    }
}
