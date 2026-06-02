package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.annotations.ManagedMetric
import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webflux.ReactiveOperations
import kotlinx.coroutines.runBlocking
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webflux.TestSupport
import io.github.hchanjune.omk.webflux.TestSupport.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.aspectj.lang.reflect.MethodSignature
import org.aspectj.lang.reflect.SourceLocation
import org.aspectj.runtime.internal.AroundClosure
import reactor.core.publisher.Mono
import reactor.util.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// ── Stub annotations ──────────────────────────────────────────────────────────

@ManagedController
private class StubController

private class StubEventHolder {
    @ManagedEventHandler fun handleEvent(payload: String) {}
}

@ManagedService
private class StubService

@ManagedRepository
private class StubRepo

private class StubMetricHolder {
    @ManagedMetric("explicit-metric") fun named() {}
    @ManagedMetric                    fun unnamed() {}
}

private class StubOperationHolder {
    @ManagedOperation(operation = "CreateOrder", useCase = "standard") fun withName() {}
    @ManagedOperation                                                   fun noName() {}
}

// ── FakeJoinPoint ────────────────────────────────────────────────────────────

private fun fakeJp(
    declaringType: Class<*>,
    methodName: String = "doWork",
    returns: Any? = "ok",
    throws: Throwable? = null,
    returnType: Class<*> = String::class.java,
    args: Array<Any?> = arrayOf("placeholder")
): ProceedingJoinPoint = object : ProceedingJoinPoint {
    override fun `set$AroundClosure`(arc: AroundClosure?) {}
    override fun proceed(): Any? = throws?.let { throw it } ?: returns
    override fun proceed(args: Array<out Any?>?): Any? = proceed()
    override fun getArgs(): Array<Any?> = args
    override fun getSignature(): Signature = object : MethodSignature {
        override fun getName() = methodName
        override fun getDeclaringType(): Class<*> = declaringType
        override fun getDeclaringTypeName(): String = declaringType.name
        override fun getModifiers() = 0
        override fun getMethod() = String::class.java.getMethod("toString")
        override fun getReturnType(): Class<*> = returnType
        override fun getParameterNames() = arrayOf<String>()
        override fun getParameterTypes() = arrayOf<Class<*>>()
        override fun getExceptionTypes() = arrayOf<Class<*>>()
        override fun toString() = "${declaringType.simpleName}.$methodName"
        override fun toShortString() = toString()
        override fun toLongString() = toString()
    }
    override fun getThis(): Any? = null
    override fun getTarget(): Any? = null
    override fun getSourceLocation(): SourceLocation = object : SourceLocation {
        override fun getWithinType(): Class<*> = declaringType
        override fun getFileName() = "test"
        override fun getLine() = 0
        override fun getColumn() = 0
    }
    override fun getKind() = "method-execution"
    override fun getStaticPart(): JoinPoint.StaticPart = error("not needed")
    override fun toShortString() = "${declaringType.simpleName}.$methodName"
    override fun toLongString() = toShortString()
}

private fun fakeMonoJp(
    declaringType: Class<*>,
    methodName: String = "doWork",
    monoResult: Any? = "mono-ok"
): ProceedingJoinPoint = fakeJp(
    declaringType = declaringType,
    methodName = methodName,
    returns = Mono.justOrEmpty(monoResult),
    returnType = Mono::class.java
)

// ── Test class ────────────────────────────────────────────────────────────────

class AspectsDirectTest {

    private val spanIdProvider = SpanIdProvider { "asp-test-span" }

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
    fun `controller aspect propagates exception without context`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val jp = fakeJp(StubController::class.java, throws = RuntimeException("ctrl-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundController(jp, ann) }
    }

    @Test
    fun `controller aspect handles Mono return type`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val jp = fakeMonoJp(StubController::class.java)
        val result = aspect.aroundController(jp, ann)
        assertNotNull(result)
    }

    @Test
    fun `controller aspect with context pushes ENTRY span`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = context()

        val result = mono(Dispatchers.Unconfined) {
            val jp = fakeJp(StubController::class.java, args = arrayOf(coroutineContext[kotlinx.coroutines.reactor.ReactorContext]))
            aspect.aroundController(jp, ann)
        }
            .contextWrite(Context.of(io.github.hchanjune.omk.webflux.ReactiveOperations.CONTEXT_KEY, ctx))
            .block()

        assertNotNull(result)
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
    fun `operation aspect propagates exception without context`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val jp = fakeJp(StubOperationHolder::class.java, throws = IllegalStateException("op-boom"))
        assertFailsWith<IllegalStateException> { aspect.aroundOperation(jp, ann) }
    }

    @Test
    fun `operation aspect handles Mono return type`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val jp = fakeMonoJp(StubOperationHolder::class.java)
        val result = aspect.aroundOperation(jp, ann)
        assertNotNull(result)
    }

    @Test
    fun `operation aspect uses method name as span name when operation is blank`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("noName")
            .getAnnotation(ManagedOperation::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            declaringType = StubOperationHolder::class.java,
            methodName = "noName",
            args = arrayOf(createContinuationWithContext(ctx))
        )
        aspect.aroundOperation(jp, ann)
        assertEquals("StubOperationHolder.noName", ctx.rootSpan?.name?.value)
    }

    // ── ManagedServiceAspect ──────────────────────────────────────────────────

    @Test
    fun `service aspect proceeds without context`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val jp = fakeJp(StubService::class.java, returns = "svc-result")
        val result = aspect.aroundService(jp, ann)
        assertEquals("svc-result", result)
    }

    @Test
    fun `service aspect injects service name when context present`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val ctx = context()
        val jp = fakeJp(StubService::class.java, args = arrayOf(createContinuationWithContext(ctx)))
        aspect.aroundService(jp, ann)
        assertEquals("StubService", ctx.service)
    }

    @Test
    fun `service aspect handles Mono return type`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val jp = fakeMonoJp(StubService::class.java)
        val result = aspect.aroundService(jp, ann)
        assertNotNull(result)
    }

    // ── ManagedRepositoryAspect ───────────────────────────────────────────────

    @Test
    fun `repository aspect proceeds without context`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val jp = fakeJp(StubRepo::class.java, returns = listOf("row1"))
        val result = aspect.aroundRepository(jp, ann)
        assertEquals(listOf("row1"), result)
    }

    @Test
    fun `repository aspect propagates exception without context`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val jp = fakeJp(StubRepo::class.java, throws = RuntimeException("db-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundRepository(jp, ann) }
    }

    @Test
    fun `repository aspect handles Mono return type`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val jp = fakeMonoJp(StubRepo::class.java)
        val result = aspect.aroundRepository(jp, ann)
        assertNotNull(result)
    }

    @Test
    fun `repository aspect pushes DB span when context present`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val ctx = context()
        val jp = fakeJp(StubRepo::class.java, methodName = "findAll", args = arrayOf(createContinuationWithContext(ctx)))
        aspect.aroundRepository(jp, ann)
        assertNull(ctx.peek())
        assertNotNull(ctx.rootSpan)
    }

    // ── ManagedMetricAspect ───────────────────────────────────────────────────

    @Test
    fun `metric aspect proceeds without context`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val jp = fakeJp(StubMetricHolder::class.java, returns = "metric-result")
        val result = aspect.aroundMetric(jp, ann)
        assertEquals("metric-result", result)
    }

    @Test
    fun `metric aspect propagates exception without context`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val jp = fakeJp(StubMetricHolder::class.java, throws = RuntimeException("metric-boom"))
        assertFailsWith<RuntimeException> { aspect.aroundMetric(jp, ann) }
    }

    @Test
    fun `metric aspect handles Mono return type`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val jp = fakeMonoJp(StubMetricHolder::class.java)
        val result = aspect.aroundMetric(jp, ann)
        assertNotNull(result)
    }

    @Test
    fun `metric aspect uses explicit name from annotation`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = context()
        val jp = fakeJp(StubMetricHolder::class.java, methodName = "named", args = arrayOf(createContinuationWithContext(ctx)))
        aspect.aroundMetric(jp, ann)
        assertEquals("explicit-metric", ctx.rootSpan?.name?.value)
    }

    @Test
    fun `metric aspect uses class dot method when name is blank`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("unnamed")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = context()
        val jp = fakeJp(StubMetricHolder::class.java, methodName = "unnamed", args = arrayOf(createContinuationWithContext(ctx)))
        aspect.aroundMetric(jp, ann)
        assertEquals("StubMetricHolder.unnamed", ctx.rootSpan?.name?.value)
    }

    // ── ManagedEventHandlerAspect ─────────────────────────────────────────────

    private fun configureEventProviders() {
        ReactiveOperations.configureEventProviders(
            contextProvider = object : ManagedContextProvider {
                override fun provide() = ManagedContext(spanIdProvider = spanIdProvider)
            },
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "evt-trace" },
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "evt-cause" },
            generateWhenMissing = true
        )
    }

    @Test
    fun `event handler aspect handles Mono return type with existing context`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = context()
        val jp = fakeMonoJp(StubEventHolder::class.java, "handleEvent")
        val jpWithCtx = fakeJp(
            StubEventHolder::class.java,
            "handleEvent",
            returns = Mono.just("event-ok"),
            returnType = Mono::class.java,
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = runBlocking { aspect.aroundEventHandler(jpWithCtx, ann) }
        assertNotNull(result)
    }

    @Test
    fun `event handler aspect proceeds without context`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeJp(StubEventHolder::class.java, "handleEvent", returns = "ok")
        val result = runBlocking { aspect.aroundEventHandler(jp, ann) }
        assertEquals("ok", result)
    }

    @Test
    fun `event handler aspect propagates exception without context`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeJp(StubEventHolder::class.java, "handleEvent", throws = RuntimeException("evt-boom"))
        assertFailsWith<RuntimeException> { runBlocking { aspect.aroundEventHandler(jp, ann) } }
    }

    @Test
    fun `event handler aspect with existing context uses it`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubEventHolder::class.java, "handleEvent", returns = "ok",
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = runBlocking { aspect.aroundEventHandler(jp, ann) }
        assertEquals("ok", result)
    }

    @Test
    fun `event handler aspect Mono path without context`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeMonoJp(StubEventHolder::class.java, "handleEvent")
        val result = runBlocking { aspect.aroundEventHandler(jp, ann) }
        assertNotNull(result)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun createContinuationWithContext(ctx: ManagedContext): kotlin.coroutines.Continuation<Any?> {
        val reactorContext = Context.of(io.github.hchanjune.omk.webflux.ReactiveOperations.CONTEXT_KEY, ctx)
        val coroutineContext = kotlinx.coroutines.reactor.ReactorContext(reactorContext)
        return object : kotlin.coroutines.Continuation<Any?> {
            override val context = coroutineContext
            override fun resumeWith(result: Result<Any?>) {}
        }
    }
}
