package com.ddzm.media

import com.ddzm.core.contract.MediaSnapshot
import com.ddzm.core.contract.MediaStateSource
import com.ddzm.core.contract.MediaTransport
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 进程内媒体状态源（P4）。
 *
 * [MediaNotificationService] 把 MediaSession 回调写进来，音乐卡片通过 core 的
 * [MediaStateSource] 契约订阅；两边都不知道对方的具体类型。刻意不在这里做
 * Handler 线程切换：系统回调与 View 更新都在主线程，保持纯 JVM 可测。
 *
 * 未授予「通知使用权」时服务根本不会被绑定，[current] 保持空快照，
 * 卡片据此显示授权引导 —— 能力缺失不阻断桌面主流程。
 */
class MediaStateRepository : MediaStateSource {

    @Volatile
    private var snapshot: MediaSnapshot = MediaSnapshot()

    private val listeners = CopyOnWriteArrayList<(MediaSnapshot) -> Unit>()

    @Volatile
    private var transport: MediaTransport? = null

    /** 由 [MediaNotificationService] 在 onListenerConnected 时注入，解绑时清空。 */
    @Volatile
    var refreshAction: (() -> Unit)? = null

    fun publish(next: MediaSnapshot) {
        snapshot = next
        listeners.forEach { listener ->
            runCatching { listener(next) }
        }
    }

    fun attachTransport(value: MediaTransport?) {
        transport = value
    }

    fun clear() {
        attachTransport(null)
        publish(MediaSnapshot())
    }

    override fun current(): MediaSnapshot = snapshot

    override fun refresh() {
        runCatching { refreshAction?.invoke() }
    }

    /** 立即推送当前值；返回的 lambda 由 CardView.onDetach 调用。 */
    override fun subscribe(listener: (MediaSnapshot) -> Unit): () -> Unit {
        listeners.add(listener)
        runCatching { listener(snapshot) }
        return { listeners.remove(listener) }
    }

    override fun transport(): MediaTransport? = transport
}

/** Application 与 Service 属于不同组件生命周期，用进程内单例共享同一份状态。 */
object MediaStateHolder {
    val repository = MediaStateRepository()
}
