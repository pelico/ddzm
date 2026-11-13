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
 * 主界面选择。[AUTO] 按设备画像决定：大屏 / 车机 / 音箱走三区桌面，
 * 手机走上游 Olauncher 竖屏桌面。用户可在界面里强制切换，便于在同一台设备上预览。
 */
enum class HomeMode(val id: String) {
    AUTO("auto"),
    CAR("car"),
    PHONE("phone");

    companion object {
        fun fromId(id: String): HomeMode = values().firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * 用户配置。
 *
 * [pinnedCards] 的 key 是槽位 id，value 是卡片 id；由 DefaultLayoutEngine 优先绑定，
 * 未固定或固定卡片不可用时自动回退匹配。[dockPackages] 是底部 Dock 的固定应用，
 * 为空时由 appmodel 按分类自动挑选。
 */
data class LauncherConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val homeMode: HomeMode = HomeMode.AUTO,
    val activeLayoutId: String = DEFAULT_LAYOUT_ID,
    val deviceOverride: DeviceClass? = null,
    val pinnedCards: Map<String, String> = emptyMap(),
    val dockPackages: List<String> = emptyList(),
    val overlayGuidanceShown: Boolean = false,
    val splitScreenHintShown: Boolean = false
) {
    companion object {
        const val DEFAULT_LAYOUT_ID = "three-zone"
    }
}
