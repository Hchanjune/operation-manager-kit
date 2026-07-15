package io.github.hchanjune.omk.servlet.bridge

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.otel.OtelSpanBridge
import io.github.hchanjune.omk.servlet.Operations
import io.github.hchanjune.omk.servlet.TestSupport
import io.github.hchanjune.omk.servlet.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedOperationAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedRepositoryAspect
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.Signature
import org.aspectj.lang.reflect.SourceLocation
import org.aspectj.runtime.internal.AroundClosure
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ── Stub annotated types ─────────────────────────────────────────────────────

@ManagedController
private class BridgedController

private class BridgedOperationHolder {
    @ManagedOperation(operation = "CreateOrder", useCase = "standard")
    fun create() {}
}

@ManagedRepository
private class BridgedRepository

// ── JoinPoint stub with a custom body, so aspects can be nested like real calls ──

private fun jp(declaringType: Class<*>, methodName: String, body: () -> Any?): ProceedingJoinPoint =
    object : ProceedingJoinPoint {
        override fun `set$AroundClosure`(arc: AroundClosure?) {}
        override fun proceed(): Any? = body()
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

/**
 * Drives the real servlet aspects (controller → operation → repository, nested like actual
 * proxied calls) against a live [OtelSpanBridge] and asserts the Phase-1 goals empirically:
 * OMK span ids ARE the exported OTel ids, nesting is parent-linked, the current OTel context
 * is set during execution (auto-instrumentation nesting), and incoming traceparent continuity.
 */
class OtelBridgeIntegrationTest {

    private val exporter = InMemorySpanExporter.create()
    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val tracer = tracerProvider.get("omk-servlet-test")

    private val controllerAspect = ManagedControllerAspect(TestSupport.spanIdProvider)
    private val operationAspect = ManagedOperationAspect(TestSupport.spanIdProvider)
    private val repositoryAspect = ManagedRepositoryAspect(TestSupport.spanIdProvider)

    private val controllerAnn = BridgedController::class.java.getAnnotation(ManagedController::class.java)!!
    private val operationAnn = BridgedOperationHolder::class.java.getDeclaredMethod("create")
        .getAnnotation(ManagedOperation::class.java)!!
    private val repositoryAnn = BridgedRepository::class.java.getAnnotation(ManagedRepository::class.java)!!

    @BeforeTest
    fun setUp() = Operations.clear()

    @AfterTest
    fun tearDown() {
        Operations.clear()
        tracerProvider.close()
    }

    private fun bridgedContext() = TestSupport.context().also {
        it.runtime = OperationRuntime().apply { spanBridge = OtelSpanBridge(tracer, makeCurrent = true) }
        Operations.applyContext(it)
    }

    private fun runNestedFlow(repositoryBody: () -> Any? = { "row" }): Any? {
        val repoJp = jp(BridgedRepository::class.java, "findById", repositoryBody)
        val opJp = jp(BridgedOperationHolder::class.java, "create") {
            repositoryAspect.aroundRepositoryMethod(repoJp, repositoryAnn)
        }
        val ctrlJp = jp(BridgedController::class.java, "get") {
            operationAspect.aroundOperation(opJp, operationAnn)
        }
        return controllerAspect.aroundController(ctrlJp, controllerAnn)
    }

    @Test
    fun `omk span ids are the exported otel ids across the whole aspect flow`() {
        val ctx = bridgedContext()

        runNestedFlow()

        val spans = exporter.finishedSpanItems
        assertEquals(3, spans.size)

        val ctrl = spans.first { it.name == "BridgedController.get" }
        val op = spans.first { it.name == "CreateOrder" }
        val repo = spans.first { it.name == "BridgedRepository.findById" }

        // OMK tree ids == exported ids
        assertEquals(ctx.rootSpan!!.spanId, ctrl.spanId)
        assertEquals(ctx.rootSpan!!.children.single().spanId, op.spanId)
        assertEquals(ctx.rootSpan!!.children.single().children.single().spanId, repo.spanId)

        // Parent links form one tree under the adopted trace id
        assertEquals(ctrl.spanId, op.parentSpanId)
        assertEquals(op.spanId, repo.parentSpanId)
        assertTrue(spans.all { it.traceId == ctx.traceId })
    }

    @Test
    fun `current otel span is set during execution so auto-instrumentation nests`() {
        bridgedContext()
        var spanIdSeenInRepository: String? = null

        runNestedFlow(repositoryBody = {
            spanIdSeenInRepository = Span.current().spanContext.spanId
            "row"
        })

        val repo = exporter.finishedSpanItems.first { it.name == "BridgedRepository.findById" }
        assertEquals(repo.spanId, spanIdSeenInRepository)
    }

    @Test
    fun `incoming traceparent ids are preserved and parent-linked`() {
        val incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val incomingParentId = "00f067aa0ba902b7"
        val ctx = TestSupport.context(traceId = incomingTraceId, causationId = incomingParentId).also {
            it.markTraceContinuedFromRemote()
            it.runtime = OperationRuntime().apply { spanBridge = OtelSpanBridge(tracer, makeCurrent = true) }
            Operations.applyContext(it)
        }

        runNestedFlow()

        assertEquals(incomingTraceId, ctx.traceId)
        val ctrl = exporter.finishedSpanItems.first { it.name == "BridgedController.get" }
        assertEquals(incomingTraceId, ctrl.traceId)
        assertEquals(incomingParentId, ctrl.parentSpanId)
    }

    @Test
    fun `without a bridge nothing is exported and omk ids are self-generated`() {
        val ctx = TestSupport.context().also {
            it.runtime = OperationRuntime()
            Operations.applyContext(it)
        }

        runNestedFlow()

        assertTrue(exporter.finishedSpanItems.isEmpty())
        assertTrue(ctx.rootSpan!!.spanId.startsWith("test-span-"))
    }
}
