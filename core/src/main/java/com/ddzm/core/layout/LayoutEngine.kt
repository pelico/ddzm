package com.ddzm.core.layout

import com.ddzm.core.card.CardDescriptor

/**
 * 布局引擎 SPI。想换一套排布算法（如按使用频率自动排列）只需实现本接口
 * 并注册到 `scope.layouts`，core 与其他模块零改动。
 */
interface LayoutEngine {
    val id: String
    fun resolve(spec: LayoutSpec, cards: List<CardDescriptor>, context: LayoutContext): LayoutPlan
}

/**
 * 默认引擎：能力过滤 → 设备画像过滤 → 尺寸过滤 → 按槽位 acceptedKinds 顺序绑定。
 * 全程纯 Kotlin 逻辑，可在 JVM 单元测试中覆盖。
 */
class DefaultLayoutEngine : LayoutEngine {

    override val id: String = "default"

    override fun resolve(
        spec: LayoutSpec,
        cards: List<CardDescriptor>,
        context: LayoutContext
    ): LayoutPlan {
        val skipped = mutableListOf<Pair<String, String>>()
        val maxWidth = if (context.widthDp <= 0) Int.MAX_VALUE else context.widthDp

        val usable = cards.filter { card ->
            val missing = context.capabilities.missing(card.requires)
            val deviceOk = card.supportedDeviceClasses.isEmpty() ||
                context.deviceClass in card.supportedDeviceClasses
            val widthOk = card.minWidthDp <= maxWidth
            when {
                missing.isNotEmpty() ->
                    skipped += card.id to "missing capabilities: ${missing.joinToString()}"
                !deviceOk ->
                    skipped += card.id to "device class '${context.deviceClass}' not supported"
                !widthOk ->
                    skipped += card.id to "minWidthDp ${card.minWidthDp} > ${context.widthDp}"
            }
            missing.isEmpty() && deviceOk && widthOk
        }.toMutableList()

        val bindings = spec.slots.map { slot ->
            val pinned = slot.persistentCardId?.let { id -> usable.firstOrNull { it.id == id } }
            val match = pinned ?: usable.firstOrNull { card ->
                context.capabilities.hasAll(slot.requiredCapabilities) &&
                    (slot.acceptedKinds.isEmpty() || card.kind in slot.acceptedKinds) &&
                    card.minWidthDp <= maxWidth
            }
            if (match != null) {
                usable.remove(match)
                SlotBinding(slot, match.id)
            } else {
                SlotBinding(slot, null)
            }
        }
        return LayoutPlan(spec, bindings, skipped)
    }
}
