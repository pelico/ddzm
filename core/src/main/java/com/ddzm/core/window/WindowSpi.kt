package com.ddzm.core.window

import com.ddzm.core.Capability
import com.ddzm.core.CapabilitySnapshot
import com.ddzm.core.PluginRegistry

/**
 * L0 桌面内卡片 → L1 悬浮 → L2 系统分屏 → L3 真嵌入。
 * ordinal 即降级顺序，[com.ddzm.core.Framework.dispatch] 依赖这一点。
 */
enum class WindowLevel { L0_IN_CARD, L1_OVERLAY, L2_SPLIT_SCREEN, L3_EMBED }

data class WindowRequest(
    val targetPackage: String? = null,
    val targetActivity: String? = null,
    val cardId: String? = null,
    val preferredLevel: WindowLevel = WindowLevel.L0_IN_CARD,
    val extras: Map<String, String> = emptyMap()
)

/**
 * 窗口策略与 Android 之间的唯一出口。只含基本类型，
 * 因此策略逻辑可脱离 Android 框架做纯 JVM 测试；平台实现由 :app 注入。
 */
interface WindowHost {
    fun startApp(packageName: String, activityName: String? = null): Boolean
    fun showInCard(cardId: String): Boolean
    fun canDrawOverlays(): Boolean
    fun hasSplitScreenSupport(): Boolean
}

sealed class WindowResult {
    data class Handled(
        val level: WindowLevel,
        val strategyId: String,
        val detail: String = ""
    ) : WindowResult()

    data class Degraded(
        val attempted: List<String>,
        val finalLevel: WindowLevel,
        val reason: String
    ) : WindowResult()

    data class Failed(val reason: String) : WindowResult()
}

/**
 * 窗口策略 SPI。新增一种能力（freeform、PiP、厂商定制分屏等）只需实现本接口并注册。
 * [canHandle] 必须只依据运行时能力快照与请求内容判断，不得假定设备型号。
 */
interface WindowStrategy {
    val id: String
    val level: WindowLevel
    val requires: Set<Capability>

    fun canHandle(request: WindowRequest, capabilities: CapabilitySnapshot): Boolean =
        capabilities.hasAll(requires)

    fun launch(request: WindowRequest, host: WindowHost): WindowResult
}

class WindowStrategyRegistry {
    private val registry = PluginRegistry<WindowStrategy>()

    fun register(strategy: WindowStrategy): Boolean = registry.register(strategy.id, strategy)
    fun get(id: String): WindowStrategy? = registry[id]
    fun all(): List<WindowStrategy> = registry.values()
    fun forLevel(level: WindowLevel): List<WindowStrategy> = all().filter { it.level == level }
    fun size(): Int = registry.size()
}
