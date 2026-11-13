package com.ddzm.core.layout

import com.ddzm.core.Capability
import com.ddzm.core.CapabilitySnapshot

@JvmInline
value class SlotId(val value: String)

/** 槽位排列方向。P3 的车机横排与音箱竖排共用同一套 LayoutSpec。 */
enum class LayoutAxis { ROW, COLUMN }

/**
 * 布局中的一个槽位。
 *
 * [weight] 是槽位在所属区域内的占比；[group] / [groupWeight] / [groupAxis]
 * 只是**渲染元数据**（P3）：同组槽位装进同一个区域容器，区域之间按 groupWeight 分配。
 * [group] 全为空时渲染器退化为单层平铺，保持 P2 竖屏布局的行为不变。
 * 因此新增一种区域划分不需要改布局引擎，也不需要改渲染器。
 */
data class SlotSpec(
    val id: SlotId,
    val weight: Float = 1f,
    val minWidthDp: Int = 0,
    val acceptedKinds: Set<String> = emptySet(),
    val requiredCapabilities: Set<Capability> = emptySet(),
    val persistentCardId: String? = null,
    val group: String = "",
    val groupWeight: Float = 0f,
    val groupAxis: LayoutAxis = LayoutAxis.COLUMN
)

data class LayoutSpec(
    val id: String,
    val label: String,
    val slots: List<SlotSpec>,
    val supportedDeviceClasses: Set<String> = emptySet(),
    val requiresLandscape: Boolean = false,
    val axis: LayoutAxis = LayoutAxis.ROW
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
