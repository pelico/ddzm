package com.ddzm.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistryTest {

    @Test
    fun duplicateIdIsRejected() {
        val registry = PluginRegistry<String>()
        assertTrue(registry.register("clock", "first"))
        assertFalse(registry.register("clock", "second"))
        assertEquals("first", registry.require("clock"))
        assertEquals(1, registry.size())
    }

    @Test
    fun allIsSortedById() {
        val registry = PluginRegistry<Int>()
        registry.register("c", 3)
        registry.register("a", 1)
        registry.register("b", 2)
        assertEquals(listOf("a", "b", "c"), registry.all().map { it.first })
        assertEquals(listOf(1, 2, 3), registry.values())
    }

    @Test
    fun missingIdThrows() {
        val registry = PluginRegistry<String>()
        try {
            registry.require("nope")
            assertTrue("expected NoSuchElementException", false)
        } catch (expected: NoSuchElementException) {
            assertTrue(expected.message!!.contains("nope"))
        }
    }
}
