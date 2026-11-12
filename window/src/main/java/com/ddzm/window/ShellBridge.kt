package com.ddzm.window

import com.ddzm.core.contract.ShellExecutor
import com.ddzm.core.window.WindowRequest

/**
 * P2 默认桥接：普通 APK 没有 shell 身份，恒不可用。
 * P4 用 Shizuku（无线调试激活、无 root）实现替换本类即可启用 L2，
 * 无需改动 core 或任何窗口策略。
 */
class NoopShellExecutor : ShellExecutor {
    override fun available(): Boolean = false
    override fun execute(command: String): ShellExecutor.ShellResult =
        ShellExecutor.ShellResult(-1, "shell bridge unavailable")
}

/**
 * 系统分屏命令构造 SPI。各 OEM 的 `am start --windowingMode` 取值与
 * bounds 参数支持不一致，P4 依据 P1 探测结果注入实现。
 * 返回 null = 当前设备不该走 L2，[SplitScreenWindowStrategy] 会自动降级。
 */
fun interface SplitScreenCommand {
    fun build(request: WindowRequest): String?
}
