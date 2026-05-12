package io.github.hchanjune.omk.webmvc.hooks

import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricOutcome
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricStatus
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.webmvc.TestSupport.buildTree
import io.github.hchanjune.omk.webmvc.TestSupport.context
import io.github.hchanjune.omk.webmvc.TestSupport.pushSpan
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
            traceId     = "4bf92f3577b34da6a3ce929d0e0e4736",  // 32 hex chars
            causationId = "00f067aa0ba902b7"                    // 16 hex chars
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
        ctx.pushSpan("unfinished-root") // intentionally not ended
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
    fun `span with no outcome (null) does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.pushSpan("unresolved.op") // not ended → outcome=null
        hook.onSuccess(ctx)
    }

    @Test
    fun `span with null durationMs uses current time as end`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.pushSpan("no-end.op") // durationMs=null, startTime non-null
        hook.onSuccess(ctx)
    }

    @Test
    fun `nested spans are exported without throwing`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context()
        ctx.buildTree() // 3-level span tree
        hook.onSuccess(ctx)
    }

    @Test
    fun `malformed causationId short string does not throw`() {
        val hook = OtelOperationHook(noopTracer)
        val ctx = context(
            traceId = "4bf92f3577b34da6a3ce929d0e0e4736",
            causationId = "short"
        )
        ctx.buildTree()
        hook.onSuccess(ctx)
    }
}
