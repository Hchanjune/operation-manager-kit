package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedCacheRepository
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.annotations.ManagedMetric
import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.annotations.ManagedSchedule
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.contants.ManagedProtocolType
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.servlet.Operations
import io.github.hchanjune.omk.servlet.TestSupport
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.aspectj.lang.reflect.SourceLocation
import org.aspectj.runtime.internal.AroundClosure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ── Stub annotations ─────────────────────────────────────────────────────────

@ManagedController
private class StubController

private class StubEventHolder {
    @ManagedEventHandler
    fun handleEvent(payload: String) {}
}

private class StubScheduleHolder {
    @ManagedSchedule
    fun runJob() {}

    @ManagedSchedule(quietWhenEmpty = true)
    fun pollBatch(): Int = 0
}

@ManagedService
private class StubService

@ManagedRepository
private class StubRepo

@ManagedCacheRepository
private class StubCacheRepo

private class StubMetricHolder {
    @ManagedMetric("explicit-metric")
    fun named() {}

    @ManagedMetric
    fun unnamed() {}
}

private class StubOperationHolder {
    @ManagedOperation(operation = "CreateOrder", useCase = "standard")
    fun withName() {}

    @ManagedOperation
    fun noName() {}
}

// ── FakeJoinPoint helper ──────────────────────────────────────────────────────

private fun fakeJp(
    declaringType: Class<*>,
    methodName: String = "doWork",
    returns: Any? = "ok",
    throws: Throwable? = null
): ProceedingJoinPoint = object : ProceedingJoinPoint {
    override fun `set$AroundClosure`(arc: AroundClosure?) {}
    override fun proceed(): Any? = throws?.let { throw it } ?: returns
    override fun proceed(args: Array<out Any?>?): Any? = proceed()
    override fun getSignature(): Signature = object : Signature {
        override fun getName() = methodName
        override fun getDeclaringType(): Class<*> = declaringType
        override fun getDeclaringTypeName(): String = declaringType.name
        override fun getModifiers() = 0
        override fun toString() = "${declaringType.simpleName}.$methodName"
        override fun toShortString() = toString()
        override fun toLongString() = toString()
    }
    override fun getThis(): Any? = null
    override fun getTarget(): Any? = null
    override fun getArgs(): Array<Any?> = emptyArray()
    override fun getSourceLocation(): SourceLocation = object : SourceLocation {
        override fun getWithinType(): Class<*> = declaringType
        override fun getFileName() = "test"
        override fun getLine() = 0
        override fun getColumn() = 0
    }
    override fun getKind(): String = "method-execution"
    override fun getStaticPart(): JoinPoint.StaticPart = error("not needed in tests")
    override fun toShortString() = "${declaringType.simpleName}.$methodName"
    override fun toLongString() = toShortString()
}

// ─────────────────────────────────────────────────────────────────────────────

class AspectsDirectTest {

    private val spanIdProvider = SpanIdProvider { "asp-test-span" }

    @BeforeTest
    fun setUp() {
        Operations.clear()
    }

    @AfterTest
    fun tearDown() {
        Operations.clear()
    }

    // ── ManagedControllerAspect ───────────────────────────────────────────────

    @Test
    fun `controller aspect proceeds without context`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val jp = fakeJp(StubController::class.java, returns = "response")
        val result = aspect.aroundController(jp, ann)
        assertEquals("response", result)
    }

    @Test
    fun `controller aspect propagates exception`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubController::class.java, throws = RuntimeException("ctrl-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundController(jp, ann) }
    }

    @Test
    fun `controller aspect injects entrypoint when context is present`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubController::class.java, methodName = "get")
        aspect.aroundController(jp, ann)
        assertEquals("StubController", ctx.entrypoint)
    }

    // ── ManagedOperationAspect ────────────────────────────────────────────────

    @Test
    fun `operation aspect proceeds without context`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val jp = fakeJp(StubOperationHolder::class.java, returns = 42)
        val result = aspect.aroundOperation(jp, ann)
        assertEquals(42, result)
    }

    @Test
    fun `operation aspect propagates exception`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubOperationHolder::class.java, throws = IllegalStateException("op-boom"))
        assertFailsWith<IllegalStateException> { aspect.aroundOperation(jp, ann) }
    }

    @Test
    fun `operation aspect uses method name as span name when operation is blank`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("noName")
            .getAnnotation(ManagedOperation::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubOperationHolder::class.java, methodName = "noName")
        aspect.aroundOperation(jp, ann)
        // rootSpan name should be "StubOperationHolder.noName"
        assertEquals("StubOperationHolder.noName", ctx.rootSpan?.name?.value)
    }

    // ── ManagedServiceAspect ──────────────────────────────────────────────────

    @Test
    fun `service aspect proceeds without context`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val jp = fakeJp(StubService::class.java, returns = "svc-result")
        val result = aspect.injectService(jp, ann)
        assertEquals("svc-result", result)
    }

    @Test
    fun `service aspect injects service name when context present`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubService::class.java)
        aspect.injectService(jp, ann)
        assertEquals("StubService", ctx.service)
    }

    @Test
    fun `service aspect skips injectService when class name already matches`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val ctx = TestSupport.context()
        ctx.injectService("StubService")
        Operations.applyContext(ctx)
        val jp = fakeJp(StubService::class.java)
        aspect.injectService(jp, ann)
        assertEquals("StubService", ctx.service)
    }

    // ── ManagedRepositoryAspect ───────────────────────────────────────────────

    @Test
    fun `repository aspect proceeds without context`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val jp = fakeJp(StubRepo::class.java, returns = listOf("row1"))
        val result = aspect.aroundRepositoryMethod(jp, ann)
        assertEquals(listOf("row1"), result)
    }

    @Test
    fun `repository aspect propagates exception`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubRepo::class.java, throws = RuntimeException("db-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundRepositoryMethod(jp, ann) }
    }

    @Test
    fun `repository aspect pushes and pops span on success`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubRepo::class.java, methodName = "findAll")
        aspect.aroundRepositoryMethod(jp, ann)
        assertNull(ctx.peek())
    }

    // ── ManagedCacheRepositoryAspect ──────────────────────────────────────────

    @Test
    fun `cache repository aspect proceeds without context`() {
        val aspect = ManagedCacheRepositoryAspect(spanIdProvider)
        val ann = StubCacheRepo::class.java.getAnnotation(ManagedCacheRepository::class.java)!!
        val jp = fakeJp(StubCacheRepo::class.java, returns = "cached-value")
        val result = aspect.aroundCacheRepositoryMethod(jp, ann)
        assertEquals("cached-value", result)
    }

    @Test
    fun `cache repository aspect propagates exception`() {
        val aspect = ManagedCacheRepositoryAspect(spanIdProvider)
        val ann = StubCacheRepo::class.java.getAnnotation(ManagedCacheRepository::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubCacheRepo::class.java, throws = RuntimeException("cache-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundCacheRepositoryMethod(jp, ann) }
        assertNull(ctx.peek())
    }

    @Test
    fun `cache repository aspect pushes CACHE layer span and pops on success`() {
        val aspect = ManagedCacheRepositoryAspect(spanIdProvider)
        val ann = StubCacheRepo::class.java.getAnnotation(ManagedCacheRepository::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubCacheRepo::class.java, methodName = "get")
        aspect.aroundCacheRepositoryMethod(jp, ann)
        assertNull(ctx.peek())
        assertEquals(io.github.hchanjune.omk.core.metric.MetricLayer.CACHE, ctx.rootSpan?.descriptor?.layer)
    }

    // ── ManagedMetricAspect ───────────────────────────────────────────────────

    @Test
    fun `metric aspect proceeds without context`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val jp = fakeJp(StubMetricHolder::class.java, returns = "metric-result")
        val result = aspect.aroundManagedMetric(jp, ann)
        assertEquals("metric-result", result)
    }

    @Test
    fun `metric aspect propagates exception`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubMetricHolder::class.java, throws = RuntimeException("metric-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundManagedMetric(jp, ann) }
    }

    @Test
    fun `metric aspect uses explicit name from annotation`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubMetricHolder::class.java, methodName = "named")
        aspect.aroundManagedMetric(jp, ann)
        assertEquals("explicit-metric", ctx.rootSpan?.name?.value)
    }

    @Test
    fun `metric aspect uses class dot method when name is blank`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("unnamed")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubMetricHolder::class.java, methodName = "unnamed")
        aspect.aroundManagedMetric(jp, ann)
        assertEquals("StubMetricHolder.unnamed", ctx.rootSpan?.name?.value)
    }

    // ── ManagedEventHandlerAspect ─────────────────────────────────────────────

    private fun configureEventProviders() {
        Operations.configureEventProviders(
            contextProvider = object : ManagedContextProvider {
                override fun provide() = ManagedContext(spanIdProvider = spanIdProvider)
            },
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "evt-trace" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "evt-cause" },
            generateWhenMissing = true
        )
    }

    @Test
    fun `event handler aspect uses existing context when contextOwner is false`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubEventHolder::class.java, methodName = "handleEvent", returns = "ok")
        val result = aspect.aroundEventHandler(jp, ann)
        assertEquals("ok", result)
    }

    @Test
    fun `event handler aspect propagates exception when contextOwner is false`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubEventHolder::class.java, methodName = "handleEvent", throws = RuntimeException("evt-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundEventHandler(jp, ann) }
    }

    @Test
    fun `event handler aspect initializes context when no existing context`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeJp(StubEventHolder::class.java, methodName = "handleEvent", returns = "result")
        val result = aspect.aroundEventHandler(jp, ann)
        assertEquals("result", result)
        assertTrue(!Operations.hasContext)
    }

    // ── ManagedScheduleAspect ─────────────────────────────────────────────────

    @Test
    fun `initializeForSchedule creates context with generated ids and SCHEDULED scope`() {
        configureEventProviders()
        Operations.initializeForSchedule()
        val ctx = Operations.context
        assertEquals("evt-trace", ctx.traceId)
        assertEquals("evt-cause", ctx.causationId)
        assertTrue(ctx.isScheduled)
        assertEquals(ManagedProtocolType.SCHEDULED, ctx.protocol)
        assertEquals("SCHEDULED", ctx.type)
    }

    @Test
    fun `schedule aspect initializes and clears context when no existing context`() {
        configureEventProviders()
        val aspect = ManagedScheduleAspect(spanIdProvider)
        val ann = StubScheduleHolder::class.java.getDeclaredMethod("runJob")
            .getAnnotation(ManagedSchedule::class.java)!!
        val jp = fakeJp(StubScheduleHolder::class.java, methodName = "runJob", returns = "done")
        val result = aspect.aroundSchedule(jp, ann)
        assertEquals("done", result)
        assertTrue(!Operations.hasContext)
    }

    @Test
    fun `schedule aspect clears context after exception when contextOwner is true`() {
        configureEventProviders()
        val aspect = ManagedScheduleAspect(spanIdProvider)
        val ann = StubScheduleHolder::class.java.getDeclaredMethod("runJob")
            .getAnnotation(ManagedSchedule::class.java)!!
        val jp = fakeJp(StubScheduleHolder::class.java, methodName = "runJob", throws = RuntimeException("sch-fail"))
        assertFailsWith<RuntimeException> { aspect.aroundSchedule(jp, ann) }
        assertTrue(!Operations.hasContext)
    }

    @Test
    fun `schedule aspect quietWhenEmpty silences defaultLogging on empty result`() {
        configureEventProviders()
        var captured: ManagedContext? = null
        Operations.configureHook(object : io.github.hchanjune.omk.core.OperationHook {
            override fun onSuccess(context: ManagedContext) { captured = context }
        })
        val aspect = ManagedScheduleAspect(spanIdProvider)
        val ann = StubScheduleHolder::class.java.getDeclaredMethod("pollBatch")
            .getAnnotation(ManagedSchedule::class.java)!!
        val jp = fakeJp(StubScheduleHolder::class.java, methodName = "pollBatch", returns = 0)
        aspect.aroundSchedule(jp, ann)
        assertEquals(false, captured?.defaultLogging)
    }

    @Test
    fun `schedule aspect quietWhenEmpty keeps defaultLogging on non-empty result`() {
        configureEventProviders()
        var captured: ManagedContext? = null
        Operations.configureHook(object : io.github.hchanjune.omk.core.OperationHook {
            override fun onSuccess(context: ManagedContext) { captured = context }
        })
        val aspect = ManagedScheduleAspect(spanIdProvider)
        val ann = StubScheduleHolder::class.java.getDeclaredMethod("pollBatch")
            .getAnnotation(ManagedSchedule::class.java)!!
        val jp = fakeJp(StubScheduleHolder::class.java, methodName = "pollBatch", returns = 7)
        aspect.aroundSchedule(jp, ann)
        assertEquals(true, captured?.defaultLogging)
    }

    @Test
    fun `schedule aspect uses existing context when contextOwner is false`() {
        val aspect = ManagedScheduleAspect(spanIdProvider)
        val ann = StubScheduleHolder::class.java.getDeclaredMethod("runJob")
            .getAnnotation(ManagedSchedule::class.java)!!
        val ctx = TestSupport.context()
        Operations.applyContext(ctx)
        val jp = fakeJp(StubScheduleHolder::class.java, methodName = "runJob", returns = "ok")
        val result = aspect.aroundSchedule(jp, ann)
        assertEquals("ok", result)
        assertEquals("StubScheduleHolder", ctx.entrypoint)
        assertNull(ctx.peek())
        assertTrue(Operations.hasContext)
    }

    @Test
    fun `event handler aspect clears context after exception when contextOwner is true`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeJp(StubEventHolder::class.java, methodName = "handleEvent", throws = RuntimeException("evt-fail"))
        assertFailsWith<RuntimeException> { aspect.aroundEventHandler(jp, ann) }
        assertTrue(!Operations.hasContext)
    }
}
