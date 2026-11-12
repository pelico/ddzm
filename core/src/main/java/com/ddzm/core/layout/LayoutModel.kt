package com.ddzm.core.layout

import com.ddzm.core.Capability
import com.ddzm.core.CapabilitySnapshot

@JvmInline
value class SlotId(val value: String)

/** 布局中的一个槽位。[weight] 用于 P3 的横向/纵向权重分配。 */
data class SlotSpec(
    val id: SlotId,
    val weight: Float = 1f,
    val minWidthDp: Int = 0,
    val acceptedKinds: Set<String> = emptySet(),
    val requiredCapabilities: Set<Capability> = emptySet(),
    val persistentCardId: String? = null
)

data class LayoutSpec(
    val id: String,
    val label: String,
    val slots: List<SlotSpec>,
    val supportedDeviceClasses: Set<String> = emptySet(),
    val requiresLandscape: Boolean = false
) {
    fun supports(context: LayoutContext): Boolean =
        (supportedDeviceClasses.isEmpty() || context.deviceClass in supportedDeviceClasses) &&
            (!requiresLandscape || context.isLandscape)
}

/**
 * 布局解析所需的设备上下文。由 :config 提供数值，core 不直接访问 Android Configuration。
 */
data class LayoutContext(
    val deviceClass: String,
    val isLandscape: Boolean,
    val widthDp: Int,
    val heightDp: Int,
    val capabilities: CapabilitySnapshot
)

data class SlotBinding(val slot: SlotSpec, val cardId: String?)

data class LayoutPlan(
    val layout: LayoutSpec,
    val bindings: List<SlotBinding>,
    /** cardId -> 被跳过的原因，供设置页与联调日志展示。 */
    val skipped: List<Pair<String, String>> = emptyList()
)
