package com.ddzm.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ddzm.core.contract.MediaSnapshot
import com.ddzm.core.contract.MediaTransport

/**
 * P4 媒体状态入口。用户在「通知使用权」里授权本服务后，系统才会绑定它；
 * 未授权时服务不会运行，音乐卡片自动显示授权引导（L0 降级）。
 *
 * 这是氢桌面「左侧音乐」的正确做法：不嵌入第三方 Activity，只读取
 * MediaSession 的 metadata / playbackState 并由桌面自绘卡片呈现。
 */
class MediaNotificationService : NotificationListenerService() {

    private val repo get() = MediaStateHolder.repository
    private var sessionManager: MediaSessionManager? = null
    private val boundControllers = mutableListOf<MediaController>()
    private var activeController: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindSessions(controllers.orEmpty())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refreshSessions()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, MediaNotificationService::class.java)
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        sessionManager = manager
        repo.refreshAction = { refreshSessions() }
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            bindSessions(manager.getActiveSessions(component))
        }.onFailure {
            // 部分 OEM 会拒绝 getActiveSessions；保持空快照而不是崩溃桌面。
            repo.clear()
        }
    }

    override fun onListenerDisconnected() {
        runCatching {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        }
        unbindControllers()
        sessionManager = null
        repo.refreshAction = null
        repo.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 有些播放器只发 MediaStyle 通知、不触发 MediaSession 回调，补一次会话刷新。
        val extras = runCatching { sbn?.notification?.extras }.getOrNull()
        if (extras?.containsKey(EXTRA_MEDIA_SESSION) == true) refreshSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val extras = runCatching { sbn?.notification?.extras }.getOrNull()
        if (extras?.containsKey(EXTRA_MEDIA_SESSION) == true) refreshSessions()
    }

    private fun refreshSessions() {
        val manager = sessionManager ?: return
        val component = ComponentName(this, MediaNotificationService::class.java)
        runCatching { bindSessions(manager.getActiveSessions(component)) }
            .onFailure { repo.clear() }
    }

    private fun bindSessions(controllers: List<MediaController>) {
        unbindControllers()
        boundControllers.addAll(controllers)
        controllers.forEach { runCatching { it.registerCallback(controllerCallback) } }
        activeController = controllers.firstOrNull { isPlaying(it) } ?: controllers.firstOrNull()
        publish()
    }

    private fun unbindControllers() {
        boundControllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        boundControllers.clear()
        activeController = null
    }

    private fun isPlaying(controller: MediaController): Boolean =
        runCatching { controller.playbackState?.state == PlaybackState.STATE_PLAYING }
            .getOrDefault(false)

    private fun publish() {
        val controller = activeController
        if (controller == null) {
            repo.attachTransport(null)
            repo.publish(MediaSnapshot())
            return
        }
        val metadata = runCatching { controller.metadata }.getOrNull()
        val state = runCatching { controller.playbackState }.getOrNull()
        val duration = runCatching {
            metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        }.getOrDefault(0L)
        repo.attachTransport(ControllerTransport(controller))
        repo.publish(
            MediaSnapshot(
                title = runCatching {
                    metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                }.getOrNull().orEmpty(),
                artist = runCatching {
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                }.getOrNull().orEmpty(),
                album = runCatching {
                    metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                }.getOrNull().orEmpty(),
                packageName = runCatching { controller.packageName }.getOrDefault(""),
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                durationMillis = duration,
                updatedAtMillis = System.currentTimeMillis(),
                sessionActive = true
            )
        )
    }

    private class ControllerTransport(
        private val controller: MediaController
    ) : MediaTransport {
        override fun play(): Boolean = control { it.play() }
        override fun pause(): Boolean = control { it.pause() }
        override fun next(): Boolean = control { it.skipToNext() }
        override fun previous(): Boolean = control { it.skipToPrevious() }

        private inline fun control(
            block: (android.media.session.MediaController.TransportControls) -> Unit
        ): Boolean = runCatching {
            block(controller.transportControls)
            true
        }.getOrDefault(false)
    }

    private companion object {
        // Notification.EXTRA_MEDIA_SESSION 的稳定 AOSP 字面量；避免再踩 @hide 常量。
        const val EXTRA_MEDIA_SESSION = "android.mediaSession"
    }
}
