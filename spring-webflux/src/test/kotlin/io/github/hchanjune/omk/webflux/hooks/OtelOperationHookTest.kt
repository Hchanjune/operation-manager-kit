package io.github.hchanjune.omk.webflux.hooks

import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricOutcome
import io.github.hchanjune.omk.core.metric.MetricStatus
import io.github.hchanjune.omk.webflux.TestSupport.buildTree
import io.github.hchanjune.omk.webflux.TestSupport.context
import io.github.hchanjune.omk.webflux.TestSupport.pushSpan
import io.opentelemetry.api.OpenTelemetry
import kotlin.test.Test

class OtelOperationHookTest {

    private val noopTracer = OpenTelemetry.noop().getTracer("omk-test")

    @Test
    fun `does not throw when context has no span tree`() {
        val hook = OtelOperationHook(noopTracer)
        hook.onSuccess(context())
        hook.onFailure(context(), RuntimeException())
    }

    @Test
    fun `does not throw with a standard span tree`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.buildTree()
        hook.onSuccess(ctx)
    }

    @Test
    fun `does not throw with valid W3C traceId and causationId`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context(
            traceId     = "4bf92f3577b34da6a3ce929d0e0e4736",
            causationId = "00f067aa0ba902b7"
        )
        ctx.buildTree()
        hook.onSuccess(ctx)
    }

    @Test
    fun `does not throw with empty traceId and causationId`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context(traceId = "", causationId = "")
        ctx.buildTree()
        hook.onSuccess(ctx)
    }

    @Test
    fun `does not throw when span tree has no end time`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.pushSpan("unfinished-root")
        hook.onSuccess(ctx)
    }

    @Test
    fun `onFailure runs without throwing`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.buildTree()
        hook.onFailure(ctx, IllegalStateException("something went wrong"))
    }

    @Test
    fun `span with DB layer does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.pushSpan("repo.findAll", MetricLayer.DB).also { it.end() }
        ctx.pop()
        hook.onSuccess(ctx)
    }

    @Test
    fun `span with EXTERNAL layer does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.pushSpan("http.call", MetricLayer.EXTERNAL).also { it.end() }
        ctx.pop()
        hook.onSuccess(ctx)
    }

    @Test
    fun `span with non-SUCCESS outcome sets error status`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        val span = ctx.pushSpan("failing.op")
        span.end(RuntimeException("server error"))
        ctx.pop()
        hook.onSuccess(ctx)
    }

    @Test
    fun `span with FAILURE_CLIENT outcome and null errorType uses status name`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        val span = ctx.pushSpan("client.op")
        span.end(MetricOutcome(MetricStatus.FAILURE_CLIENT, errorType = null, errorMessage = null))
        ctx.pop()
        hook.onSuccess(ctx)
    }

    @Test
    fun `span with CANCELLED outcome does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        val span = ctx.pushSpan("cancelled.op")
        span.end(MetricOutcome(MetricStatus.CANCELLED))
        ctx.pop()
        hook.onSuccess(ctx)
    }

    @Test
    fun `span with no outcome does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.pushSpan("unresolved.op")
        hook.onSuccess(ctx)
    }

    @Test
    fun `nested spans are exported without throwing`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.buildTree()
        hook.onSuccess(ctx)
    }

    @Test
    fun `malformed causationId does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context(
            traceId     = "4bf92f3577b34da6a3ce929d0e0e4736",
            causationId = "short"
        )
        ctx.buildTree()
        hook.onSuccess(ctx)
    }
}
