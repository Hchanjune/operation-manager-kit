package io.github.hchanjune.omk.reactive.bridge

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.otel.OtelSpanBridge
import io.github.hchanjune.omk.reactive.ReactiveOperations
import io.github.hchanjune.omk.reactive.TestSupport
import io.github.hchanjune.omk.reactive.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedRepositoryAspect
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context as OtelContext
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.aspectj.lang.reflect.MethodSignature
import org.aspectj.lang.reflect.SourceLocation
import org.aspectj.runtime.internal.AroundClosure
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// ── Stub annotated types ─────────────────────────────────────────────────────

@ManagedController
private class BridgedController

@ManagedRepository
private class BridgedRepository

private class MonoStub {
    fun monoMethod(): Mono<String> = Mono.just("stub")
}

private val MONO_METHOD = MonoStub::class.java.getDeclaredMethod("monoMethod")

// Mono-returning JoinPoint stub (method return type is Mono, so the aspects take the isMono path)
private fun monoJp(declaringType: Class<*>, methodName: String, result: Mono<*>): ProceedingJoinPoint =
    object : ProceedingJoinPoint {
        override fun `set$AroundClosure`(arc: AroundClosure?) {}
        override fun proceed(): Any? = result
        override fun proceed(args: Array<out Any?>?): Any? = proceed()
        override fun getArgs(): Array<Any?> = emptyArray()
        override fun getSignature(): Signature = object : MethodSignature {
            override fun getName() = methodName
            override fun getDeclaringType(): Class<*> = declaringType
            override fun getDeclaringTypeName(): String = declaringType.name
            override fun getModifiers() = 0
            override fun getMethod() = MONO_METHOD
            override fun getReturnType(): Class<*> = Mono::class.java
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

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drives the real reactive aspects (controller → repository, composed like an actual Mono
 * pipeline) against a live [OtelSpanBridge] with makeCurrent=false and asserts the Phase-2(a)
 * goals empirically: OMK span ids ARE the exported OTel ids even when spans end on a
 * different thread than they started on, nesting is parent-linked, and incoming traceparent
 * continuity holds. Auto-instrumentation nesting (Reactor context propagation) is Phase 2(b).
 */
class OtelBridgeIntegrationTest {

    private val exporter = InMemorySpanExporter.create()
    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val tracer = tracerProvider.get("omk-reactive-test")

    private val controllerAspect = ManagedControllerAspect(TestSupport.spanIdProvider)
    private val repositoryAspect = ManagedRepositoryAspect(TestSupport.spanIdProvider)

    private val controllerAnn = BridgedController::class.java.getAnnotation(ManagedController::class.java)!!
    private val repositoryAnn = BridgedRepository::class.java.getAnnotation(ManagedRepository::class.java)!!

    @AfterTest
    fun tearDown() {
        tracerProvider.close()
    }

    private fun bridgedContext(): ManagedContext = TestSupport.context().also {
        it.runtime = OperationRuntime().apply { spanBridge = OtelSpanBridge(tracer, makeCurrent = false) }
    }

    /** controller(get) → repository(findById) pipeline, as the proxied aspects would compose it. */
    private fun pipeline(ctx: ManagedContext, repositorySource: Mono<*> = Mono.just("row")): Mono<*> {
        val repoMono = repositoryAspect.aroundRepository(
            monoJp(BridgedRepository::class.java, "findById", repositorySource), repositoryAnn
        ) as Mono<*>
        val ctrlMono = controllerAspect.aroundController(
            monoJp(BridgedController::class.java, "get", Mono.defer { repoMono }), controllerAnn
        ) as Mono<*>
        return ctrlMono.contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
    }

    @Test
    fun `omk span ids are the exported otel ids across the mono pipeline`() {
        val ctx = bridgedContext()

        assertEquals("row", pipeline(ctx).block())

        val spans = exporter.finishedSpanItems
        assertEquals(2, spans.size)
        val ctrl = spans.first { it.name == "BridgedController.get" }
        val repo = spans.first { it.name == "BridgedRepository.findById" }

        assertEquals(ctx.rootSpan!!.spanId, ctrl.spanId)
        assertEquals(ctx.rootSpan!!.children.single().spanId, repo.spanId)
        assertEquals(ctrl.spanId, repo.parentSpanId)
        assertTrue(spans.all { it.traceId == ctx.traceId })
    }

    @Test
    fun `spans ending on another thread than they started keep adopted ids`() {
        val ctx = bridgedContext()
        val subscribeThread = Thread.currentThread().name

        // delayElement hops the completion signal to a parallel scheduler thread,
        // so span.end() runs off the subscribing thread — must still work without scopes.
        pipeline(ctx, Mono.just("row").delayElement(Duration.ofMillis(50))).block()

        val repo = exporter.finishedSpanItems.first { it.name == "BridgedRepository.findById" }
        assertEquals(ctx.rootSpan!!.children.single().spanId, repo.spanId)
        // sanity: the pipeline really did hop threads
        assertTrue(ctx.rootSpan!!.children.single().threadName == subscribeThread)
    }

    @Test
    fun `incoming traceparent ids are preserved and parent-linked`() {
        val incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val incomingParentId = "00f067aa0ba902b7"
        val ctx = TestSupport.context(traceId = incomingTraceId, causationId = incomingParentId).also {
            it.markTraceContinuedFromRemote()
            it.runtime = OperationRuntime().apply { spanBridge = OtelSpanBridge(tracer, makeCurrent = false) }
        }

        pipeline(ctx).block()

        assertEquals(incomingTraceId, ctx.traceId)
        val ctrl = exporter.finishedSpanItems.first { it.name == "BridgedController.get" }
        assertEquals(incomingTraceId, ctrl.traceId)
        assertEquals(incomingParentId, ctrl.parentSpanId)
    }

    @Test
    fun `error path exports error status with adopted ids`() {
        val ctx = bridgedContext()

        assertFailsWith<IllegalStateException> {
            pipeline(ctx, Mono.error<String>(IllegalStateException("repo-boom"))).block()
        }

        val repo = exporter.finishedSpanItems.first { it.name == "BridgedRepository.findById" }
        assertEquals(StatusCode.ERROR, repo.status.statusCode)
        assertEquals(ctx.rootSpan!!.children.single().spanId, repo.spanId)
    }

    @Test
    fun `concurrent pipelines keep their traces separate`() {
        val contexts = (1..30).map { bridgedContext() }

        Mono.zip(contexts.map { ctx ->
            pipeline(ctx, Mono.just("row").delayElement(Duration.ofMillis(20))).map { it.toString() }
        }) { it.size }.block()

        val spans = exporter.finishedSpanItems
        assertEquals(60, spans.size)
        contexts.forEach { ctx ->
            val mine = spans.filter { it.traceId == ctx.traceId }
            assertEquals(2, mine.size)
            assertEquals(ctx.rootSpan!!.spanId, mine.first { it.name == "BridgedController.get" }.spanId)
        }
        assertEquals(30, spans.map { it.traceId }.distinct().size)
    }

    @Test
    fun `otel-instrumented client inside the pipeline nests under the deepest omk span`() {
        val ctx = bridgedContext()

        // Reads its parent from the Reactor context exactly like OTel's WebClient/R2DBC
        // instrumentation does — must land under the repository span, not a detached root.
        val instrumentedClientCall = Mono.deferContextual { view ->
            val otelCtx = ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, OtelContext.current())
            val clientSpan = tracer.spanBuilder("webclient.call").setParent(otelCtx).startSpan()
            clientSpan.end()
            Mono.just("row")
        }

        pipeline(ctx, instrumentedClientCall).block()

        val repo = exporter.finishedSpanItems.first { it.name == "BridgedRepository.findById" }
        val client = exporter.finishedSpanItems.first { it.name == "webclient.call" }
        assertEquals(repo.spanId, client.parentSpanId)
        assertEquals(ctx.traceId, client.traceId)
    }

    @Test
    fun `without a bridge nothing is exported and omk ids are self-generated`() {
        val ctx = TestSupport.context().also { it.runtime = OperationRuntime() }

        pipeline(ctx).block()

        assertTrue(exporter.finishedSpanItems.isEmpty())
        assertTrue(ctx.rootSpan!!.spanId.startsWith("test-span-"))
    }
}
