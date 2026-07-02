package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.annotations.ManagedMetric
import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.ReactiveOperations
import kotlinx.coroutines.runBlocking
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.reactive.TestSupport
import io.github.hchanjune.omk.reactive.TestSupport.context
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
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

// Stub whose method return type is Mono — used so isMono() returns true
private class MonoStub {
    fun monoMethod(): Mono<String> = Mono.just("stub-result")
}
private val MONO_METHOD = MonoStub::class.java.getDeclaredMethod("monoMethod")

// Stub with a real suspend method — used so isNullContinuation() returns true.
// Must NOT be private: CoroutinesUtils invokes the method via reflection from a different package,
// which requires the declaring class to be publicly accessible.
class SuspendStub {
    suspend fun doWork(): String = "suspended"
}
private val SUSPEND_METHOD = SuspendStub::class.java.getDeclaredMethod("doWork", Continuation::class.java)
    .also { it.isAccessible = true }

// ── FakeJoinPoint ────────────────────────────────────────────────────────────

private fun fakeJp(
    declaringType: Class<*>,
    methodName: String = "doWork",
    returns: Any? = "ok",
    throws: Throwable? = null,
    returnType: Class<*> = String::class.java,
    method: java.lang.reflect.Method = String::class.java.getMethod("toString"),
    args: Array<Any?> = arrayOf("placeholder"),
    target: Any? = null
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
        override fun getMethod() = method
        override fun getReturnType(): Class<*> = returnType
        override fun getParameterNames() = arrayOf<String>()
        override fun getParameterTypes() = arrayOf<Class<*>>()
        override fun getExceptionTypes() = arrayOf<Class<*>>()
        override fun toString() = "${declaringType.simpleName}.$methodName"
        override fun toShortString() = toString()
        override fun toLongString() = toString()
    }
    override fun getThis(): Any? = null
    override fun getTarget(): Any? = target
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

// fakeMonoJp: proceed() returns a Mono but isMono() is still false (legacy helper, keeps existing tests unchanged)
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

// fakeProperMonoJp: method return type is Mono, so isMono() returns true
private fun fakeProperMonoJp(
    declaringType: Class<*>,
    methodName: String = "doWork",
    monoResult: Any? = "mono-ok",
    args: Array<Any?> = emptyArray()
): ProceedingJoinPoint = fakeJp(
    declaringType = declaringType,
    methodName = methodName,
    returns = Mono.justOrEmpty(monoResult),
    returnType = Mono::class.java,
    method = MONO_METHOD,
    args = args
)

// fakeNullContinuationJp: last arg is null + method has Continuation param, so isNullContinuation() returns true
private fun fakeNullContinuationJp(
    declaringType: Class<*>,
    methodName: String = "doWork",
    suspendTarget: Any = SuspendStub()
): ProceedingJoinPoint = fakeJp(
    declaringType = declaringType,
    methodName = methodName,
    method = SUSPEND_METHOD,
    args = arrayOf(null),
    target = suspendTarget
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
            .contextWrite(Context.of(io.github.hchanjune.omk.reactive.ReactiveOperations.CONTEXT_KEY, ctx))
            .block()

        assertNotNull(result)
    }

    @Test
    fun `controller aspect isMono path without reactor context returns mono unchanged`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val jp = fakeProperMonoJp(StubController::class.java)
        val result = (aspect.aroundController(jp, ann) as Mono<*>).block()
        assertEquals("mono-ok", result)
    }

    @Test
    fun `controller aspect isMono path with reactor context pushes and pops span`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = context()
        val jp = fakeProperMonoJp(StubController::class.java, "getOrder")
        val result = (aspect.aroundController(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("mono-ok", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `controller aspect suspend path when proceed returns Mono wraps it with span`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubController::class.java,
            methodName = "getOrder",
            returns = Mono.just("order-data"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = (aspect.aroundController(jp, ann) as Mono<*>).block()
        assertEquals("order-data", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `controller aspect null continuation path calls proceedAsMono`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val jp = fakeNullContinuationJp(StubController::class.java)
        val result = aspect.aroundController(jp, ann)
        assertTrue(result is Mono<*>)
    }

    @Test
    fun `controller aspect isMono path records exception when the Mono errors`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = context()
        val ex = IllegalStateException("mono-boom")
        val jp = fakeJp(
            StubController::class.java,
            returns = Mono.error<String>(ex),
            returnType = Mono::class.java,
            method = MONO_METHOD
        )
        val result = (aspect.aroundController(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
        assertFailsWith<IllegalStateException> { result.block() }
        assertEquals(ex, ctx.capturedException)
    }

    @Test
    fun `controller aspect suspend path records exception and pops span when proceed throws synchronously`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = context()
        val ex = IllegalStateException("sync-boom")
        val jp = fakeJp(
            StubController::class.java,
            methodName = "getOrder",
            throws = ex,
            args = arrayOf(createContinuationWithContext(ctx))
        )
        assertFailsWith<IllegalStateException> { aspect.aroundController(jp, ann) }
        assertEquals(ex, ctx.capturedException)
        assertNull(ctx.peek())
        assertNotNull(ctx.rootSpan)
    }

    @Test
    fun `controller aspect suspend path records exception when proceed returns an erroring Mono`() {
        val aspect = ManagedControllerAspect(spanIdProvider)
        val ann = StubController::class.java.getAnnotation(ManagedController::class.java)!!
        val ctx = context()
        val ex = IllegalStateException("suspend-mono-boom")
        val jp = fakeJp(
            StubController::class.java,
            methodName = "getOrder",
            returns = Mono.error<String>(ex),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        assertFailsWith<IllegalStateException> { (aspect.aroundController(jp, ann) as Mono<*>).block() }
        assertEquals(ex, ctx.capturedException)
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

    @Test
    fun `operation aspect isMono path without reactor context returns mono unchanged`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val jp = fakeProperMonoJp(StubOperationHolder::class.java)
        val result = (aspect.aroundOperation(jp, ann) as Mono<*>).block()
        assertEquals("mono-ok", result)
    }

    @Test
    fun `operation aspect isMono path with reactor context injects annotation info`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val ctx = context()
        val jp = fakeProperMonoJp(StubOperationHolder::class.java, "withName")
        val result = (aspect.aroundOperation(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("mono-ok", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `operation aspect suspend path when proceed returns Mono wraps it with span`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubOperationHolder::class.java,
            methodName = "withName",
            returns = Mono.just("op-data"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = (aspect.aroundOperation(jp, ann) as Mono<*>).block()
        assertEquals("op-data", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `operation aspect suspend path pops span when proceed throws synchronously`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubOperationHolder::class.java,
            methodName = "withName",
            throws = IllegalStateException("op-sync-boom"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        assertFailsWith<IllegalStateException> { aspect.aroundOperation(jp, ann) }
        assertNull(ctx.peek())
        assertNotNull(ctx.rootSpan)
    }

    @Test
    fun `operation aspect null continuation path calls proceedAsMono`() {
        val aspect = ManagedOperationAspect(spanIdProvider)
        val ann = StubOperationHolder::class.java.getDeclaredMethod("withName")
            .getAnnotation(ManagedOperation::class.java)!!
        val jp = fakeNullContinuationJp(StubOperationHolder::class.java)
        val result = aspect.aroundOperation(jp, ann)
        assertTrue(result is Mono<*>)
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

    @Test
    fun `service aspect isMono path with reactor context injects service name`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val ctx = context()
        val jp = fakeProperMonoJp(StubService::class.java)
        val result = (aspect.aroundService(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("mono-ok", result)
        assertEquals("StubService", ctx.service)
    }

    @Test
    fun `service aspect suspend path when proceed returns Mono applies service injection`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubService::class.java,
            returns = Mono.just("svc-data"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = (aspect.aroundService(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("svc-data", result)
    }

    @Test
    fun `service aspect null continuation path calls proceedAsMono`() {
        val aspect = ManagedServiceAspect()
        val ann = StubService::class.java.getAnnotation(ManagedService::class.java)!!
        val jp = fakeNullContinuationJp(StubService::class.java)
        val result = aspect.aroundService(jp, ann)
        assertTrue(result is Mono<*>)
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

    @Test
    fun `repository aspect isMono path with reactor context pushes and pops DB span`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val ctx = context()
        val jp = fakeProperMonoJp(StubRepo::class.java, "findAll")
        val result = (aspect.aroundRepository(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("mono-ok", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `repository aspect suspend path when proceed returns Mono wraps with DB span`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubRepo::class.java,
            methodName = "findAll",
            returns = Mono.just("rows"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = (aspect.aroundRepository(jp, ann) as Mono<*>).block()
        assertEquals("rows", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `repository aspect suspend path pops span when proceed throws synchronously`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubRepo::class.java,
            methodName = "findAll",
            throws = RuntimeException("db-sync-boom"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        assertFailsWith<RuntimeException> { aspect.aroundRepository(jp, ann) }
        assertNull(ctx.peek())
        assertNotNull(ctx.rootSpan)
    }

    @Test
    fun `repository aspect null continuation path calls proceedAsMono`() {
        val aspect = ManagedRepositoryAspect(spanIdProvider)
        val ann = StubRepo::class.java.getAnnotation(ManagedRepository::class.java)!!
        val jp = fakeNullContinuationJp(StubRepo::class.java)
        val result = aspect.aroundRepository(jp, ann)
        assertTrue(result is Mono<*>)
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

    @Test
    fun `metric aspect isMono path with reactor context pushes and pops span`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = context()
        val jp = fakeProperMonoJp(StubMetricHolder::class.java, "named")
        val result = (aspect.aroundMetric(jp, ann) as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("mono-ok", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `metric aspect suspend path when proceed returns Mono wraps with span`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubMetricHolder::class.java,
            methodName = "named",
            returns = Mono.just("metric-data"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = (aspect.aroundMetric(jp, ann) as Mono<*>).block()
        assertEquals("metric-data", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `metric aspect suspend path pops span when proceed throws synchronously`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubMetricHolder::class.java,
            methodName = "named",
            throws = RuntimeException("metric-sync-boom"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        assertFailsWith<RuntimeException> { aspect.aroundMetric(jp, ann) }
        assertNull(ctx.peek())
        assertNotNull(ctx.rootSpan)
    }

    @Test
    fun `metric aspect null continuation path calls proceedAsMono`() {
        val aspect = ManagedMetricAspect(spanIdProvider)
        val ann = StubMetricHolder::class.java.getDeclaredMethod("named")
            .getAnnotation(ManagedMetric::class.java)!!
        val jp = fakeNullContinuationJp(StubMetricHolder::class.java)
        val result = aspect.aroundMetric(jp, ann)
        assertTrue(result is Mono<*>)
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

    @Test
    fun `event handler aspect isMono path contextOwner=true creates new context`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeProperMonoJp(StubEventHolder::class.java, "handleEvent")
        // subscribe without reactor context → contextOwner=true → initializes new context
        val result = (runBlocking { aspect.aroundEventHandler(jp, ann) } as Mono<*>).block()
        assertEquals("mono-ok", result)
    }

    @Test
    fun `event handler aspect isMono path contextOwner=false uses existing context`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = context()
        val jp = fakeProperMonoJp(StubEventHolder::class.java, "handleEvent")
        // subscribe with context → contextOwner=false → wraps existing context
        val result = (runBlocking { aspect.aroundEventHandler(jp, ann) } as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertEquals("mono-ok", result)
        assertNull(ctx.peek())
    }

    @Test
    fun `event handler aspect null continuation path without context creates new context`() {
        configureEventProviders()
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val jp = fakeNullContinuationJp(StubEventHolder::class.java, "handleEvent")
        val result = (runBlocking { aspect.aroundEventHandler(jp, ann) } as Mono<*>).block()
        assertNotNull(result)
    }

    @Test
    fun `event handler aspect null continuation path with context uses existing context`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = context()
        val jp = fakeNullContinuationJp(StubEventHolder::class.java, "handleEvent")
        val result = (runBlocking { aspect.aroundEventHandler(jp, ann) } as Mono<*>)
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()
        assertNotNull(result)
        assertNull(ctx.peek())
    }

    @Test
    fun `event handler aspect existingCtx suspend path pops span when proceed throws synchronously`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubEventHolder::class.java, "handleEvent",
            throws = RuntimeException("evt-sync-boom"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        assertFailsWith<RuntimeException> { runBlocking { aspect.aroundEventHandler(jp, ann) } }
        assertNull(ctx.peek())
        assertNotNull(ctx.rootSpan)
    }

    @Test
    fun `event handler aspect suspend existing context when proceed returns Mono wraps with span`() {
        val aspect = ManagedEventHandlerAspect(spanIdProvider)
        val ann = StubEventHolder::class.java.getDeclaredMethod("handleEvent", String::class.java)
            .getAnnotation(ManagedEventHandler::class.java)!!
        val ctx = context()
        val jp = fakeJp(
            StubEventHolder::class.java,
            "handleEvent",
            returns = Mono.just("event-mono"),
            args = arrayOf(createContinuationWithContext(ctx))
        )
        val result = (runBlocking { aspect.aroundEventHandler(jp, ann) } as Mono<*>).block()
        assertEquals("event-mono", result)
        assertNull(ctx.peek())
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun createContinuationWithContext(ctx: ManagedContext): kotlin.coroutines.Continuation<Any?> {
        val reactorContext = Context.of(io.github.hchanjune.omk.reactive.ReactiveOperations.CONTEXT_KEY, ctx)
        val coroutineContext = kotlinx.coroutines.reactor.ReactorContext(reactorContext)
        return object : kotlin.coroutines.Continuation<Any?> {
            override val context = coroutineContext
            override fun resumeWith(result: Result<Any?>) {}
        }
    }
}
