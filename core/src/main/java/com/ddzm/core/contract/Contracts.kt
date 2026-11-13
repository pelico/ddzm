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

// ---- P4：媒体状态契约 ----
// 「左边音乐 App」在氢桌面路线里不是嵌入第三方 Activity，而是桌面自绘一张
// 音乐卡片读取 MediaSession。契约只含基本类型，:media 实现、:card 消费，
// 两者互不依赖；替换成车载媒体 SDK 时无需改动卡片或 core。

data class MediaSnapshot(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val packageName: String = "",
    val isPlaying: Boolean = false,
    val durationMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val sessionActive: Boolean = false
) {
    val hasTrack: Boolean get() = title.isNotBlank() || artist.isNotBlank()
}

/** 播放控制。实现方负责把异常吃掉并转成 false，卡片不做权限判断。 */
interface MediaTransport {
    fun play(): Boolean
    fun pause(): Boolean
    fun next(): Boolean
    fun previous(): Boolean
}

/**
 * 媒体状态源。[subscribe] 立即回调一次当前值，返回值是取消订阅函数，
 * 由 CardView.onDetach 调用，避免 CardHostView 重绘造成监听泄漏。
 */
interface MediaStateSource {
    fun current(): MediaSnapshot
    fun refresh()
    fun subscribe(listener: (MediaSnapshot) -> Unit): () -> Unit
    fun transport(): MediaTransport?
}
