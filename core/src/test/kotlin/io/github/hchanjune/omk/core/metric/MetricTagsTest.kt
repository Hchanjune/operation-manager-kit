package io.github.hchanjune.omk.core.metric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricTagsTest {

    @Test
    fun `builder ignores null value`() {
        val tags = MetricTags.Builder().put("key", null).build()
        assertTrue(tags.values.isEmpty())
    }

    @Test
    fun `builder ignores blank value`() {
        val tags = MetricTags.Builder().put("key", "   ").build()
        assertTrue(tags.values.isEmpty())
    }

    @Test
    fun `builder stores valid entries`() {
        val tags = MetricTags.Builder()
            .put("a", "1")
            .put("b", "2")
            .build()
        assertEquals(mapOf("a" to "1", "b" to "2"), tags.values)
    }

    @Test
    fun `builder put overwrites existing key`() {
        val tags = MetricTags.Builder()
            .put("key", "old")
            .put("key", "new")
            .build()
        assertEquals("new", tags.values["key"])
        assertEquals(1, tags.values.size)
    }

    @Test
    fun `putAll merges all entries from another MetricTags`() {
        val base  = MetricTags.Builder().put("a", "1").build()
        val extra = MetricTags.Builder().put("b", "2").build()
        val merged = MetricTags.Builder().putAll(base).putAll(extra).build()
        assertEquals(mapOf("a" to "1", "b" to "2"), merged.values)
    }

    @Test
    fun `putAll does not mutate the original`() {
        val original = MetricTags.Builder().put("a", "1").build()
        MetricTags.Builder().putAll(original).put("b", "2").build()
        assertEquals(1, original.values.size)
    }

    @Test
    fun `empty returns an empty tag set`() {
        assertTrue(MetricTags.empty().values.isEmpty())
    }

    @Test
    fun `toBuilder creates an independent mutable copy`() {
        val original = MetricTags.Builder().put("a", "1").build()
        val modified = original.toBuilder().put("b", "2").build()
        assertEquals(1, original.values.size)
        assertEquals(2, modified.values.size)
    }
}
