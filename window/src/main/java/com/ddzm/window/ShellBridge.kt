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
 * bounds 参数支持不一致，由外部桥接（Shizuku / adb）注入实现。
 * 返回 null = 当前设备不该走 L2，[SplitScreenWindowStrategy] 会自动降级。
 */
fun interface SplitScreenCommand {
    fun build(request: WindowRequest): String?
}

/** 执行分屏命令后用来复核窗口状态的 shell 命令。 */
const val SPLIT_SCREEN_VERIFY_COMMAND = "dumpsys activity activities"

/**
 * P4：分屏结果验证 SPI。
 *
 * docs/DEVICE-FINDINGS.md 实测：荣耀 X50 GT 对 `--windowingMode 3/4` 返回成功，
 * 但 `splitScreenState` 全为 0、bounds 仍全屏 —— **命令退出码 0 不能当作分屏成功**。
 * 因此 L2 必须再跑一次运行时验证，不通过就强制回退全屏启动并继续降级。
 */
fun interface SplitScreenVerifier {
    fun verified(dumpsysOutput: String): Boolean

    companion object {
        /** 默认实现：永不通过。干净设备不依赖 Shizuku / adb，L2 直接降级。 */
        val NEVER: SplitScreenVerifier = SplitScreenVerifier { false }
    }
}

/**
 * 基于 dumpsys 的保守验证：只有输出里明确出现非零 splitScreenState 才算成功，
 * 字段缺失、解析不到、全部为 0 一律判失败（宁可降级，也不假装分屏成功）。
 */
class DumpsysSplitScreenVerifier : SplitScreenVerifier {
    override fun verified(dumpsysOutput: String): Boolean {
        if (dumpsysOutput.isBlank()) return false
        val states = STATE_PATTERN.findAll(dumpsysOutput)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
        return states.isNotEmpty() && states.any { it > 0 }
    }

    private companion object {
        val STATE_PATTERN = Regex(
            "splitScreen(?:State)?\\s*[:=]\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        )
    }
}
