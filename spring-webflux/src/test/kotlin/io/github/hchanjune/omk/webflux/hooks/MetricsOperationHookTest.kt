package io.github.hchanjune.omk.webflux.hooks

import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.webflux.TestSupport.buildTree
import io.github.hchanjune.omk.webflux.TestSupport.context
import io.github.hchanjune.omk.webflux.TestSupport.pushSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsOperationHookTest {

    private fun recorder(sink: MutableList<MetricSpan>): MetricsRecorder = MetricsRecorder { sink.add(it) }

    @Test
    fun `records every span in the tree via DFS`() {
        val recorded = mutableListOf<MetricSpan>()
        val hook = MetricsOperationHook(recorder(recorded))
        val ctx = context()
        val (root, service, db) = ctx.buildTree()

        hook.onSuccess(ctx)

        assertEquals(3, recorded.size)
        assertEquals(listOf(root, service, db), recorded)
    }

    @Test
    fun `records deep nested spans`() {
        val recorded = mutableListOf<MetricSpan>()
        val hook = MetricsOperationHook(recorder(recorded))
        val ctx = context()

        val root  = ctx.pushSpan("root")
        val child = ctx.pushSpan("child")
        val grand = ctx.pushSpan("grand")
        grand.end(); ctx.pop()
        child.end(); ctx.pop()
        root.end();  ctx.pop()

        hook.onSuccess(ctx)

        assertEquals(listOf("root", "child", "grand"), recorded.map { it.name.value })
    }

    @Test
    fun `does nothing when context has no root span`() {
        val recorded = mutableListOf<MetricSpan>()
        val hook = MetricsOperationHook(recorder(recorded))
        val ctx = context()

        hook.onSuccess(ctx)

        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `isMetricsRecorded prevents double recording`() {
        val recorded = mutableListOf<MetricSpan>()
        val hook = MetricsOperationHook(recorder(recorded))
        val ctx = context()
        ctx.buildTree()

        hook.onSuccess(ctx)
        hook.onSuccess(ctx)

        assertEquals(3, recorded.size)
    }

    @Test
    fun `onFailure also records all spans`() {
        val recorded = mutableListOf<MetricSpan>()
        val hook = MetricsOperationHook(recorder(recorded))
        val ctx = context()
        ctx.buildTree()

        hook.onFailure(ctx, RuntimeException())

        assertEquals(3, recorded.size)
    }
}
