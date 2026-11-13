package com.ddzm.config

import com.ddzm.core.CapabilitySnapshot
import com.ddzm.core.layout.LayoutContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 布局目录的纯 JVM 回归：新增布局时若破坏分组元数据或降级规则，CI 会立刻拦下。
 */
class LayoutCatalogTest {

    private val carContext = LayoutContext(
        deviceClass = "car",
        isLandscape = true,
        widthDp = 1200,
        heightDp = 720,
        capabilities = CapabilitySnapshot.EMPTY
    )

    private val phoneContext = LayoutContext(
        deviceClass = "phone",
        isLandscape = false,
        widthDp = 360,
        heightDp = 800,
        capabilities = CapabilitySnapshot.EMPTY
    )

    @Test
    fun `six layouts with unique ids`() {
        val all = LayoutCatalog.all()
        assertEquals(6, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(all.map { it.id }.containsAll(
            listOf(
                LayoutCatalog.THREE_ZONE, LayoutCatalog.WIDE_CENTER, LayoutCatalog.DUAL,
                LayoutCatalog.STACK, LayoutCatalog.FOCUS, LayoutCatalog.PHONE
            )
        ))
    }

    @Test
    fun `slot ids are unique within each layout`() {
        for (layout in LayoutCatalog.all()) {
            val ids = layout.slots.map { it.id.value }
            assertEquals("${layout.id} has duplicate slot ids", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `group metadata is consistent inside every region`() {
        for (layout in LayoutCatalog.carLayouts()) {
            val groups = layout.slots.groupBy { it.group }
            assertTrue("${layout.id} must have named regions", groups.keys.all { it.isNotBlank() })
            for ((name, slots) in groups) {
                val weights = slots.map { it.groupWeight }.toSet()
                val axes = slots.map { it.groupAxis }.toSet()
                assertEquals("${layout.id}/$name groupWeight must agree", 1, weights.size)
                assertEquals("${layout.id}/$name groupAxis must agree", 1, axes.size)
                assertTrue("${layout.id}/$name weight must be positive", weights.first() > 0f)
            }
        }
    }

    @Test
    fun `layout switch cycles through all car layouts`() {
        var id = LayoutCatalog.THREE_ZONE
        val seen = mutableSetOf(id)
        repeat(4) {
            id = LayoutCatalog.nextCarLayout(id)
            seen += id
        }
        assertEquals(5, seen.size)
        assertEquals(LayoutCatalog.THREE_ZONE, LayoutCatalog.nextCarLayout(id))
        assertEquals(LayoutCatalog.THREE_ZONE, LayoutCatalog.nextCarLayout(LayoutCatalog.PHONE))
    }

    @Test
    fun `pinned cards keep region metadata`() {
        val spec = LayoutCatalog.find(LayoutCatalog.THREE_ZONE)
        val original = spec.slots.first()
        val pinned = LayoutCatalog.withPinnedCards(spec, mapOf(original.id.value to "clock"))
        assertEquals("clock", pinned.slots.first().persistentCardId)
        assertEquals(original.group, pinned.slots.first().group)
        assertTrue(original.groupWeight == pinned.slots.first().groupWeight)
        assertEquals(original.groupAxis, pinned.slots.first().groupAxis)
        assertNotEquals(original.persistentCardId, pinned.slots.first().persistentCardId)
    }

    @Test
    fun `select falls back to phone layout on small screens`() {
        assertEquals(
            LayoutCatalog.PHONE,
            LayoutCatalog.select(LayoutCatalog.THREE_ZONE, phoneContext).id
        )
        assertEquals(
            LayoutCatalog.THREE_ZONE,
            LayoutCatalog.select(LayoutCatalog.THREE_ZONE, carContext).id
        )
    }

    @Test
    fun `stack layout is column oriented for portrait speakers`() {
        val stack = LayoutCatalog.find(LayoutCatalog.STACK)
        assertEquals(com.ddzm.core.layout.LayoutAxis.COLUMN, stack.axis)
        assertEquals(false, stack.requiresLandscape)
        assertTrue(stack.slots.any { it.groupAxis == com.ddzm.core.layout.LayoutAxis.ROW })
    }
}
