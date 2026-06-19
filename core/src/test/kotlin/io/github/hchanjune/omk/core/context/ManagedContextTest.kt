package io.github.hchanjune.omk.core.context

import io.github.hchanjune.omk.core.contants.ExecutionScope
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedContextTest {

    private val clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val idCounter = AtomicInteger()
    private val spanIdProvider = SpanIdProvider { "span-${idCounter.incrementAndGet()}" }

    private fun ctx() = ManagedContext(clock, spanIdProvider)

    private fun ManagedContext.pushSpan(name: String) = push(
        name = MetricName(name),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.empty(),
        descriptor = MetricDescriptor(layer = MetricLayer.APPLICATION),
        idProvider = spanIdProvider
    )

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Test
    fun `start sets startMillis`() {
        val ctx = ctx()
        assertEquals(0L, ctx.startMillis)
        ctx.start()
        assertEquals(clock.millis(), ctx.startMillis)
    }

    @Test
    fun `start is idempotent - second call does not overwrite`() {
        val ctx = ctx()
        ctx.start()
        val first = ctx.startMillis
        ctx.start()
        assertEquals(first, ctx.startMillis)
    }

    @Test
    fun `end without start does nothing`() {
        val ctx = ctx()
        ctx.end()
        assertEquals(0L, ctx.endMillis)
        assertEquals(0L, ctx.durationMs)
    }

    @Test
    fun `end after start sets endMillis and durationMs`() {
        val ctx = ctx()
        ctx.start()
        ctx.end()
        assertTrue(ctx.endMillis > 0)
        assertTrue(ctx.durationMs >= 0)
    }

    @Test
    fun `end is idempotent - second call does not overwrite endMillis`() {
        val ctx = ctx()
        ctx.start()
        ctx.end()
        val firstEnd = ctx.endMillis
        ctx.end()
        assertEquals(firstEnd, ctx.endMillis)
    }

    // ── Span stack ─────────────────────────────────────────────────────────

    @Test
    fun `push on empty stack creates root span`() {
        val ctx = ctx()
        assertNull(ctx.rootSpan)
        ctx.pushSpan("root")
        assertNotNull(ctx.rootSpan)
        assertEquals("root", ctx.rootSpan!!.name.value)
    }

    @Test
    fun `push on non-empty stack adds child to current top`() {
        val ctx = ctx()
        val root = ctx.pushSpan("root")
        val child = ctx.pushSpan("child")
        assertEquals(1, root.children.size)
        assertEquals(child, root.children[0])
        assertEquals(root, child.parent)
    }

    @Test
    fun `pop removes and returns the top span`() {
        val ctx = ctx()
        ctx.pushSpan("root")
        val child = ctx.pushSpan("child")
        val popped = ctx.pop()
        assertEquals(child, popped)
        assertEquals("root", ctx.peek()?.name?.value)
    }

    @Test
    fun `pop on empty stack returns null`() {
        assertNull(ctx().pop())
    }

    @Test
    fun `isFinished reflects stack state`() {
        val ctx = ctx()
        assertTrue(ctx.isFinished())
        ctx.pushSpan("root")
        assertFalse(ctx.isFinished())
        ctx.pop()
        assertTrue(ctx.isFinished())
    }

    // ── Flags ──────────────────────────────────────────────────────────────

    @Test
    fun `hooksExecuted flag starts false and is set once`() {
        val ctx = ctx()
        assertFalse(ctx.isHooksExecuted())
        ctx.markHooksExecuted()
        assertTrue(ctx.isHooksExecuted())
    }

    @Test
    fun `metricsRecorded flag starts false and is set once`() {
        val ctx = ctx()
        assertFalse(ctx.isMetricsRecorded())
        ctx.markMetricsRecorded()
        assertTrue(ctx.isMetricsRecorded())
    }

    // ── Execution scope ───────────────────────────────────────────────────

    @Test
    fun `markAsEvent sets EVENT scope`() {
        val ctx = ctx()
        assertFalse(ctx.isEvent)
        ctx.markAsEvent()
        assertTrue(ctx.isEvent)
        assertEquals(ExecutionScope.EVENT, ctx.executionScope)
    }

    @Test
    fun `enableAsyncHook and disableAsyncHook toggle the flag`() {
        val ctx = ctx()
        assertFalse(ctx.isAsyncHookEnabled)
        ctx.enableAsyncHook()
        assertTrue(ctx.isAsyncHookEnabled)
        ctx.disableAsyncHook()
        assertFalse(ctx.isAsyncHookEnabled)
    }

    // ── forkAsync ─────────────────────────────────────────────────────────

    @Test
    fun `forkAsync copies all fields into child context`() {
        val ctx = ctx().apply {
            injectTraceId("trace-1")
            injectCausationId("causation-1")
            injectIssuer("user-1")
            injectProtocol("HTTP")
            injectType("REST")
            injectHttpInfo("/orders", "POST")
            injectEntryPoint("OrderController")
            injectService("OrderService")
            injectAnnotationInfo("CreateOrder", "PlaceOrder")
            message = "test message"
            enableAsyncHook()
        }

        val fork = ctx.forkAsync()

        assertEquals("trace-1",       fork.traceId)
        assertEquals("causation-1",   fork.causationId)
        assertEquals("user-1",        fork.issuer)
        assertEquals("OrderController", fork.entrypoint)
        assertEquals("OrderService",  fork.service)
        assertEquals("CreateOrder",   fork.operation)
        assertEquals("PlaceOrder",    fork.useCase)
        assertEquals("test message",  fork.message)
        assertTrue(fork.isAsyncHookEnabled)
    }

    @Test
    fun `forkAsync sets ASYNC execution scope`() {
        val fork = ctx().forkAsync()
        assertEquals(ExecutionScope.ASYNC, fork.executionScope)
        assertTrue(fork.isAsync)
        assertFalse(fork.isEvent)
    }

    @Test
    fun `forkAsync creates async execution root span`() {
        val fork = ctx().forkAsync()
        assertNotNull(fork.rootSpan)
        assertEquals("async.execution", fork.rootSpan!!.name.value)
    }

    @Test
    fun `forkAsync produces an independent span tree`() {
        val ctx = ctx()
        ctx.pushSpan("main-root")
        val fork = ctx.forkAsync()
        assertEquals("async.execution", fork.rootSpan!!.name.value)
        assertEquals("main-root", ctx.rootSpan!!.name.value)
    }

    // ── Status code / outcome ────────────────────────────────────────────

    @Test
    fun `outcome defaults to SUCCESS before any status code is injected`() {
        val ctx = ctx()
        assertNull(ctx.statusCode)
        assertEquals(OperationOutcome.SUCCESS, ctx.outcome)
    }

    @Test
    fun `injectStatusCode maps 2xx and 3xx to SUCCESS`() {
        val ctx = ctx()
        ctx.injectStatusCode(200)
        assertEquals(200, ctx.statusCode)
        assertEquals(OperationOutcome.SUCCESS, ctx.outcome)

        ctx.injectStatusCode(302)
        assertEquals(OperationOutcome.SUCCESS, ctx.outcome)
    }

    @Test
    fun `injectStatusCode maps 401 to UNAUTHENTICATED`() {
        val ctx = ctx()
        ctx.injectStatusCode(401)
        assertEquals(OperationOutcome.UNAUTHENTICATED, ctx.outcome)
    }

    @Test
    fun `injectStatusCode maps 403 to FORBIDDEN`() {
        val ctx = ctx()
        ctx.injectStatusCode(403)
        assertEquals(OperationOutcome.FORBIDDEN, ctx.outcome)
    }

    @Test
    fun `injectStatusCode maps other 4xx to CLIENT_ERROR`() {
        val ctx = ctx()
        ctx.injectStatusCode(404)
        assertEquals(OperationOutcome.CLIENT_ERROR, ctx.outcome)

        ctx.injectStatusCode(400)
        assertEquals(OperationOutcome.CLIENT_ERROR, ctx.outcome)
    }

    @Test
    fun `injectStatusCode maps 5xx to SERVER_ERROR`() {
        val ctx = ctx()
        ctx.injectStatusCode(500)
        assertEquals(OperationOutcome.SERVER_ERROR, ctx.outcome)

        ctx.injectStatusCode(503)
        assertEquals(OperationOutcome.SERVER_ERROR, ctx.outcome)
    }

    // ── Captured exception ───────────────────────────────────────────────

    @Test
    fun `capturedException defaults to null`() {
        val ctx = ctx()
        assertNull(ctx.capturedException)
    }

    @Test
    fun `recordException stores the exception`() {
        val ctx = ctx()
        val ex = IllegalStateException("boom")
        ctx.recordException(ex)
        assertEquals(ex, ctx.capturedException)
    }

    @Test
    fun `recordException keeps the first exception when called multiple times`() {
        val ctx = ctx()
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")
        ctx.recordException(first)
        ctx.recordException(second)
        assertEquals(first, ctx.capturedException)
    }

    // ── Hook records ──────────────────────────────────────────────────────

    @Test
    fun `recordHookSuccess appends a success record`() {
        val ctx = ctx()
        ctx.recordHookSuccess("HookA")
        assertEquals(1, ctx.hookRecords.size)
        assertTrue(ctx.hookRecords[0].success)
        assertEquals("HookA", ctx.hookRecords[0].hookName)
    }

    @Test
    fun `recordHookFailure appends a failure record with error`() {
        val ctx = ctx()
        val ex = RuntimeException("oops")
        ctx.recordHookFailure("HookB", ex)
        assertEquals(1, ctx.hookRecords.size)
        assertFalse(ctx.hookRecords[0].success)
        assertEquals(ex, ctx.hookRecords[0].error)
    }

    @Test
    fun `multiple hook records preserve insertion order`() {
        val ctx = ctx()
        ctx.recordHookSuccess("First")
        ctx.recordHookFailure("Second", RuntimeException())
        ctx.recordHookSuccess("Third")
        assertEquals(listOf("First", "Second", "Third"), ctx.hookRecords.map { it.hookName })
    }
}
