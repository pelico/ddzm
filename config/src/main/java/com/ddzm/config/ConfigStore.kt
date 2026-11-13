package com.ddzm.config

import android.content.Context
import com.ddzm.core.contract.LauncherConfigStore

interface ConfigStore : LauncherConfigStore {
    fun load(): LauncherConfig
    fun save(config: LauncherConfig)
    fun update(transform: (LauncherConfig) -> LauncherConfig): LauncherConfig
    fun refreshDeviceProfile()
}

/**
 * SharedPreferences 实现。刻意不引入 JSON/序列化库，配置结构变化时可平滑迁移。
 * 设备画像只在构造与显式 refresh 时读取，避免每次布局解析都访问 Resources。
 */
class SharedPrefsConfigStore(private val appContext: Context) : ConfigStore {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var profile: DeviceProfile = DeviceProfileDetector.detect(appContext)

    override fun load(): LauncherConfig {
        val pinned = prefs.getString(KEY_PINNED_CARDS, "").orEmpty()
        val pinnedCards = pinned.split(ENTRY_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(KEY_VALUE_SEPARATOR, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else null
            }
            .toMap()

        val overrideId = prefs.getString(KEY_DEVICE_OVERRIDE, null)
        val dock = prefs.getString(KEY_DOCK_PACKAGES, "").orEmpty()
            .split(LIST_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return LauncherConfig(
            themeMode = ThemeMode.fromId(
                prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.id) ?: ThemeMode.SYSTEM.id
            ),
            homeMode = HomeMode.fromId(
                prefs.getString(KEY_HOME_MODE, HomeMode.AUTO.id) ?: HomeMode.AUTO.id
            ),
            activeLayoutId = prefs.getString(KEY_ACTIVE_LAYOUT, LauncherConfig.DEFAULT_LAYOUT_ID)
                ?: LauncherConfig.DEFAULT_LAYOUT_ID,
            deviceOverride = overrideId?.let { DeviceClass.fromId(it) },
            pinnedCards = pinnedCards,
            dockPackages = dock,
            overlayGuidanceShown = prefs.getBoolean(KEY_OVERLAY_HINT, false),
            splitScreenHintShown = prefs.getBoolean(KEY_SPLIT_HINT, false)
        )
    }

    override fun save(config: LauncherConfig) {
        prefs.edit()
            .putString(KEY_THEME_MODE, config.themeMode.id)
            .putString(KEY_HOME_MODE, config.homeMode.id)
            .putString(KEY_ACTIVE_LAYOUT, config.activeLayoutId)
            .putString(KEY_DEVICE_OVERRIDE, config.deviceOverride?.id)
            .putString(
                KEY_PINNED_CARDS,
                config.pinnedCards.entries.joinToString(ENTRY_SEPARATOR) {
                    "${it.key}$KEY_VALUE_SEPARATOR${it.value}"
                }
            )
            .putString(KEY_DOCK_PACKAGES, config.dockPackages.joinToString(LIST_SEPARATOR))
            .putBoolean(KEY_OVERLAY_HINT, config.overlayGuidanceShown)
            .putBoolean(KEY_SPLIT_HINT, config.splitScreenHintShown)
            .apply()
    }

    override fun update(transform: (LauncherConfig) -> LauncherConfig): LauncherConfig {
        val next = transform(load())
        save(next)
        return next
    }

    override fun refreshDeviceProfile() {
        profile = DeviceProfileDetector.detect(appContext)
    }

    override fun deviceClass(): String = load().deviceOverride?.id ?: profile.deviceClass.id
    override fun isLandscape(): Boolean = profile.isLandscape
    override fun screenWidthDp(): Int = profile.widthDp
    override fun screenHeightDp(): Int = profile.heightDp
    override fun themeMode(): String = load().themeMode.id
    override fun activeLayoutId(): String = load().activeLayoutId
    override fun pinnedCardFor(slotId: String): String? = load().pinnedCards[slotId]

    private companion object {
        const val PREFS_NAME = "ddzm_config"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HOME_MODE = "home_mode"
        const val KEY_ACTIVE_LAYOUT = "active_layout"
        const val KEY_DEVICE_OVERRIDE = "device_override"
        const val KEY_PINNED_CARDS = "pinned_cards"
        const val KEY_DOCK_PACKAGES = "dock_packages"
        const val KEY_OVERLAY_HINT = "overlay_hint"
        const val KEY_SPLIT_HINT = "split_hint"
        const val ENTRY_SEPARATOR = ";"
        const val KEY_VALUE_SEPARATOR = "="
        const val LIST_SEPARATOR = ","
    }
}
