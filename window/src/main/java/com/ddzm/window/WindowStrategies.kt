package com.ddzm.window

import com.ddzm.core.Capability
import com.ddzm.core.CapabilitySnapshot
import com.ddzm.core.contract.ShellExecutor
import com.ddzm.core.window.WindowHost
import com.ddzm.core.window.WindowLevel
import com.ddzm.core.window.WindowRequest
import com.ddzm.core.window.WindowResult
import com.ddzm.core.window.WindowStrategy

/**
 * L0：桌面内自绘卡片。零权限、全设备可用，是整条降级链的终点。
 */
class InCardWindowStrategy : WindowStrategy {
    override val id: String = "window.in_card"
    override val level: WindowLevel = WindowLevel.L0_IN_CARD
    override val requires: Set<Capability> = emptySet()

    override fun canHandle(request: WindowRequest, capabilities: CapabilitySnapshot): Boolean = true

    override fun launch(request: WindowRequest, host: WindowHost): WindowResult {
        val cardId = request.cardId ?: return WindowResult.Failed("no cardId for L0")
        return if (host.showInCard(cardId)) {
            WindowResult.Handled(WindowLevel.L0_IN_CARD, id, "shown in card: $cardId")
        } else {
            WindowResult.Failed("host refused in-card display")
        }
    }
}

/**
 * L1（主力）：悬浮路线。桌面本身只绘制卡片，地图/导航等第三方内容
 * 由目标 App 自己的悬浮窗提供（高德车机悬浮共存版一类）。
 * 因此这里只需保证悬浮权限 + 把目标 App 拉起来，不做 Activity 嵌入。
 */
class OverlayWindowStrategy : WindowStrategy {
    override val id: String = "window.overlay"
    override val level: WindowLevel = WindowLevel.L1_OVERLAY
    override val requires: Set<Capability> = setOf(Capability.OVERLAY_WINDOW)

    override fun canHandle(request: WindowRequest, capabilities: CapabilitySnapshot): Boolean =
        capabilities.has(Capability.OVERLAY_WINDOW) && request.targetPackage != null

    override fun launch(request: WindowRequest, host: WindowHost): WindowResult {
        val target = request.targetPackage ?: return WindowResult.Failed("no target package")
        if (!host.canDrawOverlays()) return WindowResult.Failed("overlay permission missing")
        return if (host.startApp(target, request.targetActivity)) {
            WindowResult.Handled(
                WindowLevel.L1_OVERLAY, id,
                "target=$target; floating window is drawn by the target app"
            )
        } else {
            WindowResult.Failed("cannot start $target")
        }
    }
}

/**
 * L2（条件增强）：shell 身份下触发系统分屏。P2 只搭接口与降级链，
 * 具体命令由 [SplitScreenCommand] 在 P4 注入；未注入时本策略恒不接管。
 */
class SplitScreenWindowStrategy(
    private val shell: ShellExecutor,
    private val command: SplitScreenCommand = SplitScreenCommand { null }
) : WindowStrategy {
    override val id: String = "window.split_screen"
    override val level: WindowLevel = WindowLevel.L2_SPLIT_SCREEN
    override val requires: Set<Capability> = setOf(Capability.SHELL_SPLIT_SCREEN)

    override fun canHandle(request: WindowRequest, capabilities: CapabilitySnapshot): Boolean =
        capabilities.has(Capability.SHELL_SPLIT_SCREEN) &&
            shell.available() &&
            request.targetPackage != null &&
            command.build(request) != null

    override fun launch(request: WindowRequest, host: WindowHost): WindowResult {
        val cmd = command.build(request)
            ?: return WindowResult.Failed("split-screen command not configured")
        if (!host.hasSplitScreenSupport()) {
            return WindowResult.Failed("device has no split-screen support")
        }
        val result = shell.execute(cmd)
        return if (result.exitCode == 0) {
            WindowResult.Handled(WindowLevel.L2_SPLIT_SCREEN, id, result.output)
        } else {
            WindowResult.Failed("shell returned ${result.exitCode}: ${result.output}")
        }
    }
}

/**
 * L3（仅保留 SPI）：ActivityView / CarActivityView / TaskView 属于隐藏 API，
 * 需系统签名或定制 ROM。无 root 设备上 canHandle 恒 false，永不参与调度。
 */
class EmbeddedWindowStrategy : WindowStrategy {
    override val id: String = "window.embed"
    override val level: WindowLevel = WindowLevel.L3_EMBED
    override val requires: Set<Capability> = setOf(Capability.SYSTEM_ACTIVITY_EMBED)

    override fun canHandle(request: WindowRequest, capabilities: CapabilitySnapshot): Boolean = false

    override fun launch(request: WindowRequest, host: WindowHost): WindowResult =
        WindowResult.Failed("ActivityView/TaskView requires system signature or custom ROM")
}
