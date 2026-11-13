package com.ddzm.core

import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.LayoutPlan
import com.ddzm.core.layout.LayoutSpec
import com.ddzm.core.window.WindowHost
import com.ddzm.core.window.WindowLevel
import com.ddzm.core.window.WindowRequest
import com.ddzm.core.window.WindowResult

/**
 * # ddzm 框架门面
 *
 * ## 分层
 * `core` 只放契约、注册表、纯逻辑；`config` / `appmodel` / `window` / `card`
 * 是插件实现；`app` 负责组装。依赖方向单向，core 不 import 任何业务模块。
 *
 * ## 两个扩展点（新增实现时 core 零改动）
 * 1. 卡片：实现 [com.ddzm.core.card.CardFactory]，把
 *    [com.ddzm.core.card.CardDescriptor] 与工厂注册进 `scope.cards`。
 *    能力过滤、设备画像过滤、槽位匹配由 [com.ddzm.core.layout.DefaultLayoutEngine] 统一处理。
 * 2. 窗口策略：实现 [com.ddzm.core.window.WindowStrategy] 并注册进 `scope.windows`。
 *    [dispatch] 自动按级别降级，策略本身不参与调度。
 *
 * ## 降级原则
 * 任何高等级能力缺失都不允许阻断主流程：L3 → L2 → L1 → L0，
 * L0（桌面内卡片）是永远可用的终点。能力只能由运行时探测确认，
 * 不得用 Build.VERSION 或编译期开关假定成立。
 *
 * ## 能力分级
 * L0 桌面内卡片 / L1 悬浮卡片 + 第三方 App 自身悬浮窗 / L2 系统分屏（需 shell 桥接）/
 * L3 ActivityView 嵌入（需系统签名，普通设备不可达，仅保留 SPI）。
 */
object Framework {

    const val DEFAULT_LAYOUT_ENGINE = "default"

    fun newScope(capabilities: CapabilitySnapshot = CapabilitySnapshot.EMPTY): LauncherScope =
        LauncherScope(capabilities = capabilities)

    /**
     * 按当前能力快照解析布局，返回每个槽位绑定的卡片 id（可能为空）。
     *
     * [contextOverride] 用于「预览某种设备布局」「设置里强制设备画像」等场景：
     * 渲染逻辑不变，只替换设备上下文；不传时仍走 [LauncherScope.layoutContext]。
     */
    fun resolveLayout(
        scope: LauncherScope,
        spec: LayoutSpec,
        engineId: String = DEFAULT_LAYOUT_ENGINE,
        contextOverride: LayoutContext? = null
    ): LayoutPlan {
        val engine = scope.layouts.require(engineId)
        val context = contextOverride ?: scope.layoutContext()
        return engine.resolve(spec, scope.cards.available(context), context)
    }

    /**
     * 从 [WindowRequest.preferredLevel] 起由高到低尝试所有可用策略。
     * 首个 [WindowResult.Handled] 即为结果；若经历过失败则包装为
     * [WindowResult.Degraded] 并记录尝试链，便于真机联调定位。
     */
    fun dispatch(scope: LauncherScope, request: WindowRequest, host: WindowHost): WindowResult {
        val attempted = mutableListOf<String>()
        val levels = WindowLevel.values()
            .filter { it.ordinal <= request.preferredLevel.ordinal }
            .sortedByDescending { it.ordinal }

        for (level in levels) {
            for (strategy in scope.windows.forLevel(level)) {
                if (!strategy.canHandle(request, scope.capabilities)) continue
                attempted += "${strategy.id}@${level.name}"
                when (val result = strategy.launch(request, host)) {
                    is WindowResult.Handled -> {
                        if (attempted.size <= 1) return result
                        return WindowResult.Degraded(
                            attempted = attempted,
                            finalLevel = result.level,
                            reason = "higher level strategies failed: " +
                                attempted.dropLast(1).joinToString()
                        )
                    }
                    is WindowResult.Degraded -> return result
                    is WindowResult.Failed -> continue
                }
            }
        }
        return WindowResult.Failed(
            "no strategy handled request; attempted=" +
                attempted.ifEmpty { listOf("none") }.joinToString()
        )
    }
}
