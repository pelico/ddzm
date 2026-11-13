package com.ddzm.media

import com.ddzm.core.LauncherPlugin
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys

/**
 * P4 媒体插件。只做一件事：把进程内状态源注册成 core 契约，
 * 因此音乐卡片无需 import :media，替换实现时 app 与 card 都不用改。
 */
class MediaModule(
    private val repository: MediaStateRepository = MediaStateHolder.repository
) : LauncherPlugin {

    override val id: String = "media"

    override fun install(scope: LauncherScope) {
        scope.services.put(ServiceKeys.MEDIA_STATE, repository)
    }
}
