package com.ddzm.core

import com.ddzm.core.card.CardRegistry
import com.ddzm.core.contract.LauncherConfigStore
import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.LayoutEngine
import com.ddzm.core.window.WindowStrategyRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * 一个插件单元：[requires] 中任一能力缺失则整个插件跳过安装，
 * 其余插件不受影响。插件不得假设安装顺序。
 */
interface LauncherPlugin {
    val id: String
    val requires: Set<Capability> get() = emptySet()
    fun install(scope: LauncherScope)
}

/**
 * 弱类型服务槽。core 只定义 key 常量，具体实现类型由各模块自行注册，
 * 因而新增模块不需要修改 core。跨模块取用时一律用 core 里的契约接口。
 */
class ServiceRegistry {
    private val services = ConcurrentHashMap<String, Any>()

    fun put(key: String, service: Any): Boolean = services.putIfAbsent(key, service) == null

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> find(key: String): T? = services[key] as? T

    fun <T : Any> require(key: String): T =
        find(key) ?: throw NoSuchElementException("service not registered: $key")

    fun keys(): Set<String> = services.keys.toSortedSet()
}

object ServiceKeys {
    const val CONFIG_STORE = "config.store"
    const val APP_CATALOG = "app.catalog"
    const val APP_REPOSITORY = "app.repository"
    const val CAPABILITY_PROBE = "capability.probe"
    const val SHELL_EXECUTOR = "shell.executor"
    const val WINDOW_HOST = "window.host"
}

/**
 * 框架运行期作用域。持有全部注册表与当前能力快照；
 * 配置通过 [LauncherConfigStore] 契约读取，避免 core 反向依赖 :config。
 */
class LauncherScope(
    val cards: CardRegistry = CardRegistry(),
    val windows: WindowStrategyRegistry = WindowStrategyRegistry(),
    val layouts: PluginRegistry<LayoutEngine> = PluginRegistry(),
    val services: ServiceRegistry = ServiceRegistry(),
    var capabilities: CapabilitySnapshot = CapabilitySnapshot.EMPTY
) {
    private val installed = LinkedHashSet<String>()

    /** @return false 表示能力不足或重复安装，未产生任何副作用。 */
    fun install(plugin: LauncherPlugin): Boolean {
        if (capabilities.missing(plugin.requires).isNotEmpty()) return false
        if (!installed.add(plugin.id)) return false
        plugin.install(this)
        return true
    }

    fun installedPlugins(): Set<String> = installed.toSet()

    fun refreshCapabilities(probe: CapabilityProbe): CapabilitySnapshot {
        capabilities = probe.probe()
        return capabilities
    }

    fun layoutContext(): LayoutContext {
        val store: LauncherConfigStore? = services.find(ServiceKeys.CONFIG_STORE)
        return LayoutContext(
            deviceClass = store?.deviceClass() ?: "phone",
            isLandscape = store?.isLandscape() ?: false,
            widthDp = store?.screenWidthDp() ?: 0,
            heightDp = store?.screenHeightDp() ?: 0,
            capabilities = capabilities
        )
    }
}
