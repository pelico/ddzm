package com.ddzm.core.card

import android.content.Context
import android.view.View
import com.ddzm.core.Capability
import com.ddzm.core.PluginRegistry
import com.ddzm.core.ServiceRegistry
import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.SlotSpec

/**
 * 卡片元数据。[kind] 是布局槽位匹配的唯一依据，新增卡片种类无需改 core。
 */
data class CardDescriptor(
    val id: String,
    val kind: String,
    val title: String,
    val requires: Set<Capability> = emptySet(),
    val supportedDeviceClasses: Set<String> = emptySet(),
    val minWidthDp: Int = 0,
    val minHeightDp: Int = 0
) {
    fun isAvailable(context: LayoutContext): Boolean =
        context.capabilities.hasAll(requires) &&
            (supportedDeviceClasses.isEmpty() || context.deviceClass in supportedDeviceClasses)
}

interface CardView {
    val view: View
    fun onBind(context: CardContext)
    fun onDetach()
}

data class CardContext(
    val appContext: Context,
    val slot: SlotSpec,
    val layoutContext: LayoutContext,
    val services: ServiceRegistry
)

fun interface CardFactory {
    fun create(context: CardContext): CardView
}

class CardRegistry {
    private val descriptors = PluginRegistry<CardDescriptor>()
    private val factories = PluginRegistry<CardFactory>()

    /** @return false 表示 id 已被占用；两个内部注册表保持原子一致。 */
    fun register(descriptor: CardDescriptor, factory: CardFactory): Boolean {
        require(descriptor.id.isNotBlank()) { "card id must not be blank" }
        if (descriptors[descriptor.id] != null || factories[descriptor.id] != null) return false
        descriptors.register(descriptor.id, descriptor)
        return factories.register(descriptor.id, factory)
    }

    fun descriptor(id: String): CardDescriptor? = descriptors[id]
    fun all(): List<CardDescriptor> = descriptors.values()
    fun available(context: LayoutContext): List<CardDescriptor> =
        all().filter { it.isAvailable(context) }
    fun create(id: String, context: CardContext): CardView? = factories[id]?.create(context)
    fun size(): Int = descriptors.size()
}
