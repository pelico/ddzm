package com.ddzm.config

import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.LayoutSpec
import com.ddzm.core.layout.SlotId
import com.ddzm.core.layout.SlotSpec

/**
 * 布局目录。P2 提供车机三区与竖屏降级两套基础布局，
 * P3 在此扩展为 6 种可切换布局（不改 core、不改渲染入口）。
 *
 * [withPinnedCards] 把用户固定的卡片写进槽位，
 * 从而让 DefaultLayoutEngine 的 persistentCardId 优先绑定真正生效。
 */
object LayoutCatalog {

    const val THREE_ZONE = "three-zone"
    const val PHONE = "phone"

    fun defaultSpecs(): List<LayoutSpec> = listOf(
        LayoutSpec(
            id = THREE_ZONE,
            label = "车机三区",
            slots = listOf(
                SlotSpec(SlotId("left"), weight = 0.22f, acceptedKinds = setOf("apps")),
                SlotSpec(SlotId("center"), weight = 0.50f, acceptedKinds = setOf("nav", "clock")),
                SlotSpec(SlotId("right"), weight = 0.28f, acceptedKinds = setOf("music", "clock"))
            ),
            supportedDeviceClasses = setOf("car", "tablet", "speaker"),
            requiresLandscape = true
        ),
        LayoutSpec(
            id = PHONE,
            label = "竖屏降级",
            slots = listOf(
                SlotSpec(SlotId("top"), weight = 1f, acceptedKinds = setOf("clock")),
                SlotSpec(SlotId("apps"), weight = 2f, acceptedKinds = setOf("apps"))
            ),
            supportedDeviceClasses = setOf("phone")
        )
    )

    fun find(id: String): LayoutSpec =
        defaultSpecs().firstOrNull { it.id == id } ?: defaultSpecs().first()

    /** 当前设备不支持所选布局时降级到竖屏布局，避免横屏大屏布局在手机上塌掉。 */
    fun select(activeLayoutId: String, context: LayoutContext): LayoutSpec {
        val requested = find(activeLayoutId)
        return if (requested.supports(context)) requested else find(PHONE)
    }

    fun withPinnedCards(spec: LayoutSpec, pinned: Map<String, String>): LayoutSpec =
        spec.copy(
            slots = spec.slots.map { slot ->
                val cardId = pinned[slot.id.value]
                if (cardId.isNullOrBlank()) slot else slot.copy(persistentCardId = cardId)
            }
        )
}
