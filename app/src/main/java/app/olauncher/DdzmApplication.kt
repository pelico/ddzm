package app.olauncher

import android.app.Application
import android.util.Log
import com.ddzm.appmodel.AppModelPlugin
import com.ddzm.appmodel.PackageManagerAppRepository
import com.ddzm.card.CardModule
import com.ddzm.config.ConfigPlugin
import com.ddzm.config.LayoutCatalog
import com.ddzm.config.SharedPrefsConfigStore
import com.ddzm.core.Framework
import com.ddzm.core.LauncherScope
import com.ddzm.core.ServiceKeys
import com.ddzm.core.layout.DefaultLayoutEngine
import com.ddzm.window.NoopShellExecutor
import com.ddzm.window.WindowCapabilityProbe
import com.ddzm.window.WindowModule

/**
 * 框架组装点。这里是唯一知道所有模块具体类型的地方；
 * 其余模块彼此只通过 core 的契约与注册表通信。
 *
 * P2 阶段不影响 Olauncher 原有 UI：MainActivity 与既有 Fragment 保持原样，
 * P3 起逐步把主界面迁移到 CardHostView + LayoutSpec。
 */
class DdzmApplication : Application() {

    lateinit var scope: LauncherScope
        private set

    private lateinit var capabilityProbe: WindowCapabilityProbe

    override fun onCreate() {
        super.onCreate()

        val shell = NoopShellExecutor()
        capabilityProbe = WindowCapabilityProbe(this, shell)
        val configStore = SharedPrefsConfigStore(this)
        val newScope = Framework.newScope(capabilityProbe.probe())
        scope = newScope

        newScope.layouts.register(Framework.DEFAULT_LAYOUT_ENGINE, DefaultLayoutEngine())
        newScope.services.put(ServiceKeys.WINDOW_HOST, AndroidWindowHost(this))

        val plugins = listOf(
            ConfigPlugin(configStore),
            AppModelPlugin(PackageManagerAppRepository(this)),
            WindowModule(capabilityProbe, shell),
            CardModule()
        )
        val installed = plugins.map { plugin -> plugin.id to newScope.install(plugin) }

        // P2 自检：走通「配置 -> 布局选择 -> 能力过滤 -> 槽位绑定」全链路，结果写入 logcat。
        val config = configStore.load()
        val spec = LayoutCatalog.withPinnedCards(
            LayoutCatalog.select(config.activeLayoutId, newScope.layoutContext()),
            config.pinnedCards
        )
        val plan = Framework.resolveLayout(newScope, spec)
        Log.d(
            TAG,
            "layout=${plan.layout.id} " +
                "bindings=${plan.bindings.map { "${it.slot.id.value}=${it.cardId}" }} " +
                "skipped=${plan.skipped}"
        )

        Log.i(
            TAG,
            "framework ready: capabilities=${newScope.capabilities.granted}, " +
                "cards=${newScope.cards.size()}, windows=${newScope.windows.size()}, " +
                "device=${newScope.layoutContext().deviceClass}, plugins=$installed"
        )
    }

    /** 从设置页返回桌面时重探能力：悬浮窗授权、通知监听、Shizuku 失效都要立即反映到卡片。 */
    fun refreshCapabilities(): com.ddzm.core.CapabilitySnapshot =
        scope.refreshCapabilities(capabilityProbe)

    companion object {
        private const val TAG = "ddzm"
    }
}
