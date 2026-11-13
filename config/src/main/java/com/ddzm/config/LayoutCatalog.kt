package com.ddzm.config

import com.ddzm.core.layout.LayoutAxis
import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.LayoutSpec
import com.ddzm.core.layout.SlotId
import com.ddzm.core.layout.SlotSpec

/**
 * 布局目录（P3）。
 *
 * 6 种布局全部只由 [LayoutSpec] 描述：区域划分用 SlotSpec.group/groupWeight/groupAxis，
 * 外层方向用 LayoutSpec.axis。新增一种排版只需在此追加一个 LayoutSpec，
 * core 的布局引擎、card 的渲染器、app 的主界面**均不需要改动**。
 *
 * 槽位 kind 是唯一的卡片匹配依据；卡片可来自任意插件，目录里不写死卡片 id。
 */
object LayoutCatalog {

    const val THREE_ZONE = "three-zone"
    const val WIDE_CENTER = "wide-center"
    const val DUAL = "dual"
    const val STACK = "stack"
    const val FOCUS = "focus"
    const val PHONE = "phone"

    private val LARGE_SCREENS = setOf("car", "tablet", "speaker")

    private fun slot(
        id: String,
        weight: Float,
        kinds: Set<String>,
        group: String = "",
        groupWeight: Float = 0f,
        groupAxis: LayoutAxis = LayoutAxis.COLUMN
    ) = SlotSpec(
        id = SlotId(id),
        weight = weight,
        acceptedKinds = kinds,
        group = group,
        groupWeight = groupWeight,
        groupAxis = groupAxis
    )

    /** 可在车机 / 平板 / 带屏音箱上切换的 5 种布局，顺序即界面里的循环顺序。 */
    fun carLayouts(): List<LayoutSpec> = listOf(
        LayoutSpec(
            id = THREE_ZONE,
            label = "车机三区",
            axis = LayoutAxis.ROW,
            supportedDeviceClasses = LARGE_SCREENS,
            requiresLandscape = true,
            slots = listOf(
                slot("dock_apps", 1f, setOf("apps"), "dock", 0.18f),
                slot("main_nav", 0.72f, setOf("nav", "video"), "main", 0.54f),
                slot("main_quick", 0.28f, setOf("quick", "status"), "main", 0.54f),
                slot("side_clock", 0.34f, setOf("clock", "date"), "side", 0.28f),
                slot("side_music", 0.33f, setOf("music"), "side", 0.28f),
                slot("side_weather", 0.33f, setOf("weather", "status"), "side", 0.28f)
            )
        ),
        LayoutSpec(
            id = WIDE_CENTER,
            label = "宽屏主区",
            axis = LayoutAxis.ROW,
            supportedDeviceClasses = LARGE_SCREENS,
            requiresLandscape = true,
            slots = listOf(
                slot("dock_apps", 1f, setOf("apps"), "dock", 0.14f),
                slot("main_nav", 0.8f, setOf("nav", "video"), "main", 0.66f),
                slot("main_status", 0.2f, setOf("status", "quick"), "main", 0.66f),
                slot("side_clock", 0.5f, setOf("clock", "date"), "side", 0.20f),
                slot("side_music", 0.5f, setOf("music", "weather"), "side", 0.20f)
            )
        ),
        LayoutSpec(
            id = DUAL,
            label = "主区双栏",
            axis = LayoutAxis.ROW,
            supportedDeviceClasses = LARGE_SCREENS,
            requiresLandscape = true,
            slots = listOf(
                slot("main_nav", 0.7f, setOf("nav", "video"), "main", 0.68f),
                slot("main_apps", 0.3f, setOf("apps", "quick"), "main", 0.68f),
                slot("side_clock", 0.3f, setOf("clock", "date"), "side", 0.32f),
                slot("side_music", 0.4f, setOf("music"), "side", 0.32f),
                slot("side_weather", 0.3f, setOf("weather", "status"), "side", 0.32f)
            )
        ),
        LayoutSpec(
            id = STACK,
            label = "上下分区",
            axis = LayoutAxis.COLUMN,
            supportedDeviceClasses = LARGE_SCREENS,
            slots = listOf(
                slot("main_nav", 1f, setOf("nav", "video"), "main", 0.56f),
                slot("bottom_clock", 0.33f, setOf("clock", "date"), "bottom", 0.28f, LayoutAxis.ROW),
                slot("bottom_music", 0.34f, setOf("music"), "bottom", 0.28f, LayoutAxis.ROW),
                slot("bottom_weather", 0.33f, setOf("weather", "status"), "bottom", 0.28f, LayoutAxis.ROW),
                slot("dock_apps", 1f, setOf("apps", "quick"), "dock", 0.16f)
            )
        ),
        LayoutSpec(
            id = FOCUS,
            label = "聚焦导航",
            axis = LayoutAxis.ROW,
            supportedDeviceClasses = LARGE_SCREENS,
            requiresLandscape = true,
            slots = listOf(
                slot("dock_apps", 1f, setOf("apps"), "dock", 0.10f),
                slot("main_nav", 0.85f, setOf("nav", "video"), "main", 0.78f),
                slot("main_quick", 0.15f, setOf("quick", "status"), "main", 0.78f),
                slot("side_clock", 0.5f, setOf("clock", "date"), "side", 0.12f),
                slot("side_date", 0.5f, setOf("date", "status"), "side", 0.12f)
            )
        )
    )

    /** 竖屏手机的降级布局：单层平铺，保持 P2 行为。 */
    fun phoneLayout(): LayoutSpec = LayoutSpec(
        id = PHONE,
        label = "竖屏降级",
        axis = LayoutAxis.COLUMN,
        supportedDeviceClasses = setOf("phone"),
        slots = listOf(
            slot("top", 1f, setOf("clock", "date")),
            slot("apps", 2f, setOf("apps"))
        )
    )

    fun all(): List<LayoutSpec> = carLayouts() + phoneLayout()

    /** 兼容 P2 调用点。 */
    fun defaultSpecs(): List<LayoutSpec> = all()

    fun find(id: String): LayoutSpec = all().firstOrNull { it.id == id } ?: threeZone()

    fun threeZone(): LayoutSpec = carLayouts().first()

    fun nextCarLayout(currentId: String): String {
        val layouts = carLayouts()
        val index = layouts.indexOfFirst { it.id == currentId }
        return layouts[(index + 1).coerceAtLeast(0) % layouts.size].id
    }

    /** 当前设备不支持所选布局时降级到竖屏布局，避免横屏大屏布局在手机上塌掉。 */
    fun select(activeLayoutId: String, context: LayoutContext): LayoutSpec {
        val requested = find(activeLayoutId)
        return if (requested.supports(context)) requested else phoneLayout()
    }

    fun withPinnedCards(spec: LayoutSpec, pinned: Map<String, String>): LayoutSpec =
        spec.copy(
            slots = spec.slots.map { slot ->
                val cardId = pinned[slot.id.value]
                if (cardId.isNullOrBlank()) slot else slot.copy(persistentCardId = cardId)
            }
        )
}
