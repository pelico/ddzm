package com.ddzm.card

import com.ddzm.core.LauncherPlugin
import com.ddzm.core.LauncherScope
import com.ddzm.core.card.CardDescriptor

/**
 * 内置卡片插件（P3）。
 *
 * 新增卡片只需在此追加一行 register：布局引擎、能力过滤、槽位匹配、
 * 拖拽固定、昼夜配色全部由框架处理，core 与渲染器零改动。
 *
 * 音乐 / 导航卡片刻意不设 requires：能力未授予时卡片显示授权引导而不是整块消失，
 * 这样车机三区在干净设备上也不会出现空槽位（降级不阻断主流程）。
 */
class CardModule : LauncherPlugin {

    override val id: String = "cards.builtin"

    override fun install(scope: LauncherScope) {
        register(scope, "clock", "clock", "时钟", ClockCardFactory())
        register(scope, "date", "date", "日期", DateCardFactory())
        register(scope, "apps", "apps", "应用", AppListCardFactory())
        register(scope, "music", "music", "音乐", MusicCardFactory())
        register(scope, "nav", "nav", "导航", NavCardFactory(), setOf("car", "tablet", "speaker"))
        register(scope, "weather", "weather", "天气", WeatherCardFactory())
        register(scope, "quick", "quick", "快捷", QuickCardFactory())
        register(scope, "status", "status", "状态", StatusCardFactory())
    }

    private fun register(
        scope: LauncherScope,
        id: String,
        kind: String,
        title: String,
        factory: com.ddzm.core.card.CardFactory,
        deviceClasses: Set<String> = emptySet()
    ) {
        scope.cards.register(
            CardDescriptor(
                id = id,
                kind = kind,
                title = title,
                supportedDeviceClasses = deviceClasses
            ),
            factory
        )
    }
}
