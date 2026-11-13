package com.ddzm.card

import com.ddzm.core.Framework
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys
import com.ddzm.core.card.CardContext
import com.ddzm.core.window.WindowHost
import com.ddzm.core.window.WindowLevel
import com.ddzm.core.window.WindowRequest
import com.ddzm.core.window.WindowResult

/**
 * P4：卡片启动第三方 App 的统一入口。
 *
 * 卡片自己不判断设备能力，一律走 [Framework.dispatch] 由高到低尝试
 * L2 系统分屏 → L1 悬浮共存 → L0 桌面卡片 + 全屏启动。
 * 这正是「同一份代码在车机 / 平板 / 手机上自动降级」的落点。
 */
/**
 * 进程内记录最近一次窗口调度结果。
 *
 * CarHomeFragment 每次 onResume 都会 render()，卡片 View 随之重建；若把结果
 * 只存在卡片字段里，启动地图再返回桌面后就看不到降级到了哪一级。
 */
internal object WindowDispatchLog {
    @Volatile
    var last: String = ""
        private set

    fun record(text: String) {
        last = text
    }
}

internal object WindowLauncher {

    fun dispatch(ctx: CardContext, request: WindowRequest): WindowResult {
        val result = doDispatch(ctx, request)
        WindowDispatchLog.record(result.describe())
        return result
    }

    private fun doDispatch(ctx: CardContext, request: WindowRequest): WindowResult {
        val scope: LauncherScope? = ctx.services.find(ServiceKeys.LAUNCHER_SCOPE)
        val host: WindowHost? = ctx.services.find(ServiceKeys.WINDOW_HOST)

        if (scope == null) {
            // 框架未完整组装（例如独立预览）时仍保证按钮可用。
            val target = request.targetPackage
            val started = target != null && host?.startApp(target, request.targetActivity) == true
            return if (started) {
                WindowResult.Handled(
                    WindowLevel.L0_IN_CARD, "window.direct",
                    "scope missing; direct full-screen start"
                )
            } else {
                WindowResult.Failed("window scope/host not registered")
            }
        }
        if (host == null) return WindowResult.Failed("window host not registered")
        return Framework.dispatch(scope, request, host)
    }
}

/** 把降级链结果压缩成一行可读文本，真机联调时能直接看出落在哪一级。 */
internal fun WindowResult.describe(): String = when (this) {
    is WindowResult.Handled -> "已启动 · ${level.name} · $detail"
    is WindowResult.Degraded -> "已降级至 ${finalLevel.name} · $reason"
    is WindowResult.Failed -> "启动失败 · $reason"
}
