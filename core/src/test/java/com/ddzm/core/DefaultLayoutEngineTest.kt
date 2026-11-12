package com.ddzm.core

import com.ddzm.core.card.CardDescriptor
import com.ddzm.core.layout.DefaultLayoutEngine
import com.ddzm.core.layout.LayoutContext
import com.ddzm.core.layout.LayoutSpec
import com.ddzm.core.layout.SlotId
import com.ddzm.core.layout.SlotSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLayoutEngineTest {

    private val baseContext = LayoutContext(
        deviceClass = "car",
        isLandscape = true,
        widthDp = 1200,
        heightDp = 720,
        capabilities = CapabilitySnapshot.EMPTY
    )

    private val clock = CardDescriptor("clock", "clock", "时钟")
    private val music = CardDescriptor(
        "music", "music", "音乐",
        requires = setOf(Capability.NOTIFICATION_LISTENER)
    )
    private val nav = CardDescriptor("nav", "nav", "导航")

    @Test
    fun filtersCardsByMissingCapability() {
        val spec = LayoutSpec(
            "test", "测试",
            listOf(SlotSpec(SlotId("s1")), SlotSpec(SlotId("s2")))
        )
        val plan = DefaultLayoutEngine().resolve(spec, listOf(clock, music), baseContext)

        assertEquals(1, plan.bindings.count { it.cardId != null })
        assertEquals("clock", plan.bindings.first { it.cardId != null }.cardId)
        assertEquals("music", plan.skipped.single().first)
        assertTrue(plan.skipped.single().second.contains("NOTIFICATION_LISTENER"))
    }

    @Test
    fun bindsByAcceptedKindsInSlotOrder() {
        val context = baseContext.copy(
            capabilities = CapabilitySnapshot(granted = setOf(Capability.NOTIFICATION_LISTENER))
        )
        val spec = LayoutSpec(
            "three-zone", "三区",
            listOf(
                SlotSpec(SlotId("left"), acceptedKinds = setOf("apps")),
                SlotSpec(SlotId("center"), acceptedKinds = setOf("nav")),
                SlotSpec(SlotId("right"), acceptedKinds = setOf("music"))
            )
        )
        val plan = DefaultLayoutEngine().resolve(spec, listOf(clock, music, nav), context)

        assertNull(plan.bindings[0].cardId)
        assertEquals("nav", plan.bindings[1].cardId)
        assertEquals("music", plan.bindings[2].cardId)
    }

    @Test
    fun unsupportedDeviceClassIsSkipped() {
        val card = CardDescriptor("caronly", "clock", "仅车机", supportedDeviceClasses = setOf("car"))
        val spec = LayoutSpec("test", "测试", listOf(SlotSpec(SlotId("s1"))))
        val phoneContext = baseContext.copy(deviceClass = "phone")
        val plan = DefaultLayoutEngine().resolve(spec, listOf(card), phoneContext)

        assertNull(plan.bindings.single().cardId)
        assertTrue(plan.skipped.single().second.contains("phone"))
    }
}
