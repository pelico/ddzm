package com.ddzm.config

enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String): ThemeMode = values().firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * 用户配置。[pinnedCards] 的 key 是槽位 id，value 是卡片 id；
 * 由 DefaultLayoutEngine 优先绑定，未固定或固定卡片不可用时自动回退匹配。
 */
data class LauncherConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val activeLayoutId: String = DEFAULT_LAYOUT_ID,
    val deviceOverride: DeviceClass? = null,
    val pinnedCards: Map<String, String> = emptyMap(),
    val overlayGuidanceShown: Boolean = false,
    val splitScreenHintShown: Boolean = false
) {
    companion object {
        const val DEFAULT_LAYOUT_ID = "three-zone"
    }
}
