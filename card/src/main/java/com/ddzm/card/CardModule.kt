package com.ddzm.card

import com.ddzm.core.Capability
import com.ddzm.core.LauncherPlugin
import com.ddzm.core.LauncherScope
import com.ddzm.core.card.CardDescriptor

/**
 * 内置卡片插件。新增卡片只需在本文件（或新插件）追加一行 register，
 * core 的注册表、布局引擎、能力过滤、降级分发均无需改动。
 */
class CardModule : LauncherPlugin {

    override val id: String = "cards.builtin"

    override fun install(scope: LauncherScope) {
        scope.cards.register(
            CardDescriptor(id = "clock", kind = "clock", title = "时钟"),
            ClockCardFactory()
        )
        scope.cards.register(
            CardDescriptor(id = "apps", kind = "apps", title = "应用"),
            AppShortcutCardFactory()
        )
        scope.cards.register(
            CardDescriptor(
                id = "music", kind = "music", title = "音乐",
                requires = setOf(Capability.NOTIFICATION_LISTENER)
            ),
            MusicCardFactory()
        )
        scope.cards.register(
            CardDescriptor(
                id = "nav", kind = "nav", title = "导航",
                requires = setOf(Capability.OVERLAY_WINDOW),
                supportedDeviceClasses = setOf("car", "tablet", "speaker")
            ),
            NavCardFactory()
        )
    }
}
