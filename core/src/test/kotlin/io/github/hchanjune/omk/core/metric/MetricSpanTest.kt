package io.github.hchanjune.omk.core.metric

import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetricSpanTest {

    private fun span(name: String = "test", spanId: String = name) = MetricSpan(
        traceId = "trace-1",
        spanId = spanId,
        name = MetricName(name),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.empty(),
        descriptor = MetricDescriptor(layer = MetricLayer.APPLICATION)
    )

    @Test
    fun `startTime is set at construction`() {
        assertNotNull(span().startTime)
    }

    @Test
    fun `durationMs is null before end`() {
        assertNull(span().durationMs)
    }

    @Test
    fun `durationMs is non-negative after end`() {
        val s = span()
        s.end()
        assertTrue(s.durationMs!! >= 0)
    }

    @Test
    fun `end sets SUCCESS outcome`() {
        val s = span()
        s.end()
        assertEquals(MetricStatus.SUCCESS, s.outcome?.status)
    }

    @Test
    fun `end with server exception sets FAILURE_SERVER`() {
        val s = span()
        s.end(RuntimeException("boom"))
        assertEquals(MetricStatus.FAILURE_SERVER, s.outcome?.status)
        assertEquals("RuntimeException", s.outcome?.errorType)
    }

    @Test
    fun `end with client exception sets FAILURE_CLIENT`() {
        val s = span()
        s.end(IllegalArgumentException("bad input"))
        assertEquals(MetricStatus.FAILURE_CLIENT, s.outcome?.status)
    }

    @Test
    fun `end with CancellationException sets CANCELLED`() {
        val s = span()
        s.end(CancellationException("cancelled"))
        assertEquals(MetricStatus.CANCELLED, s.outcome?.status)
    }

    @Test
    fun `addChild links parent and child`() {
        val root = span("root")
        val child = span("child")
        root.addChild(child)
        assertEquals(root, child.parent)
        assertEquals(listOf(child), root.children)
    }

    @Test
    fun `addChild throws if child already has a parent`() {
        val root1 = span("root1")
        val root2 = span("root2")
        val child = span("child")
        root1.addChild(child)
        assertFailsWith<IllegalStateException> {
            root2.addChild(child)
        }
    }

    @Test
    fun `addChild silently ignores a span with duplicate spanId`() {
        val root = span("root")
        val child1 = span("child", spanId = "dup-id")
        val child2 = span("child2", spanId = "dup-id")
        root.addChild(child1)
        root.addChild(child2)
        assertEquals(1, root.children.size)
    }

    @Test
    fun `children list preserves insertion order`() {
        val root = span("root")
        val a = span("a", spanId = "id-a")
        val b = span("b", spanId = "id-b")
        val c = span("c", spanId = "id-c")
        root.addChild(a)
        root.addChild(b)
        root.addChild(c)
        assertEquals(listOf("a", "b", "c"), root.children.map { it.name.value })
    }
}
