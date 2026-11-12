package com.ddzm.core.contract

/**
 * 跨模块契约。只使用 Kotlin 基本类型，保证 core 与这些接口都能跑纯 JVM 单元测试，
 * 也让 :config / :appmodel 可以在不改变 core 的前提下替换实现。
 */

data class AppInfo(
    val packageName: String,
    val label: String,
    val activityName: String? = null,
    val category: String = CATEGORY_OTHER,
    val lastUsedAtMillis: Long = 0L
) {
    companion object {
        const val CATEGORY_NAV = "nav"
        const val CATEGORY_MUSIC = "music"
        const val CATEGORY_VIDEO = "video"
        const val CATEGORY_OTHER = "other"
    }
}

interface AppCatalog {
    fun list(): List<AppInfo>
    fun find(packageName: String): AppInfo?
    fun refresh()
}

/** :config 的实现契约；core 只读取这些基本类型。 */
interface LauncherConfigStore {
    fun deviceClass(): String
    fun isLandscape(): Boolean
    fun screenWidthDp(): Int
    fun screenHeightDp(): Int
    fun themeMode(): String
    fun activeLayoutId(): String
    fun pinnedCardFor(slotId: String): String?
}

/**
 * shell 身份桥接（Shizuku / adb / 系统签名等）。
 * 默认实现恒不可用，因此 L2 在干净设备上不会暴露入口。
 */
interface ShellExecutor {
    fun available(): Boolean
    fun execute(command: String): ShellResult

    data class ShellResult(val exitCode: Int, val output: String)
}
