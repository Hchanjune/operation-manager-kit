package io.github.hchanjune.omk.webmvc.hooks

import io.opentelemetry.api.OpenTelemetry
import io.github.hchanjune.omk.webmvc.TestSupport.buildTree
import io.github.hchanjune.omk.webmvc.TestSupport.context
import io.github.hchanjune.omk.webmvc.TestSupport.pushSpan
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
}
