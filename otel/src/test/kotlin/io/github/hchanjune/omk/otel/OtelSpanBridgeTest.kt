package io.github.hchanjune.omk.otel

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OtelSpanBridgeTest {

    private val exporter = InMemorySpanExporter.create()
    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val tracer = tracerProvider.get("omk-test")

    private val omkIdProvider = SpanIdProvider { "omk-generated-id" }

    @AfterTest
    fun tearDown() {
        tracerProvider.close()
    }

    private fun newContext(bridge: OtelSpanBridge): ManagedContext {
        val context = ManagedContext(spanIdProvider = omkIdProvider)
        context.runtime = OperationRuntime().apply { spanBridge = bridge }
        return context
    }

    private fun push(context: ManagedContext, name: String, layer: MetricLayer) =
        context.push(
            name = MetricName(name),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder().put("tag_key", "tag_value").build(),
            descriptor = MetricDescriptor(layer = layer),
            idProvider = omkIdProvider
        )

    @Test
    fun `omk span ids are the otel-generated ids`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = false))
        context.injectTraceId("self-generated")

        val span = push(context, "Controller.get", MetricLayer.ENTRY)
        span.end()
        context.pop()

        val exported = exporter.finishedSpanItems.single()
        assertEquals(exported.spanId, span.spanId)
        assertEquals(exported.traceId, span.traceId)
        assertNotEquals("omk-generated-id", span.spanId)
    }

    @Test
    fun `fresh trace adopts otel trace id back into the context`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = false))
        context.injectTraceId("self-generated")

        val span = push(context, "Controller.get", MetricLayer.ENTRY)

        assertEquals(32, context.traceId.length)
        assertEquals(exporterTraceIdAfter(span), context.traceId)
    }

    private fun exporterTraceIdAfter(span: io.github.hchanjune.omk.core.metric.MetricSpan): String {
        span.end()
        return exporter.finishedSpanItems.single().traceId
    }

    @Test
    fun `remote-continued trace keeps the incoming trace id and links the incoming parent`() {
        val incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736"
        val incomingParentId = "00f067aa0ba902b7"

        val context = newContext(OtelSpanBridge(tracer, makeCurrent = false))
        context.injectTraceId(incomingTraceId)
        context.injectCausationId(incomingParentId)
        context.markTraceContinuedFromRemote()

        val span = push(context, "Controller.get", MetricLayer.ENTRY)
        span.end()
        context.pop()

        assertEquals(incomingTraceId, context.traceId)
        val exported = exporter.finishedSpanItems.single()
        assertEquals(incomingTraceId, exported.traceId)
        assertEquals(incomingParentId, exported.parentSpanId)
    }

    @Test
    fun `nested spans are parent-linked with real otel ids`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = false))

        val root = push(context, "Controller.get", MetricLayer.ENTRY)
        val child = push(context, "Repository.find", MetricLayer.DB)
        child.end()
        context.pop()
        root.end()
        context.pop()

        val exportedChild = exporter.finishedSpanItems.first { it.name == "Repository.find" }
        val exportedRoot = exporter.finishedSpanItems.first { it.name == "Controller.get" }
        assertEquals(root.spanId, exportedChild.parentSpanId)
        assertEquals(exportedRoot.traceId, exportedChild.traceId)
        assertEquals(SpanKind.SERVER, exportedRoot.kind)
        assertEquals(SpanKind.CLIENT, exportedChild.kind)
        assertEquals("tag_value", exportedChild.attributes.asMap().entries.first { it.key.key == "tag_key" }.value)
    }

    @Test
    fun `failure outcome maps to otel error status`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = false))

        val span = push(context, "Controller.get", MetricLayer.ENTRY)
        span.end(IllegalStateException("boom"))
        context.pop()

        val exported = exporter.finishedSpanItems.single()
        assertEquals(StatusCode.ERROR, exported.status.statusCode)
        assertTrue(exported.status.description.contains("boom"))
    }

    @Test
    fun `makeCurrent exposes the span as otel current and closes the scope on end`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = true))

        val root = push(context, "Controller.get", MetricLayer.ENTRY)
        assertEquals(root.spanId, Span.current().spanContext.spanId)

        val child = push(context, "Repository.find", MetricLayer.DB)
        assertEquals(child.spanId, Span.current().spanContext.spanId)

        child.end()
        context.pop()
        assertEquals(root.spanId, Span.current().spanContext.spanId)

        root.end()
        context.pop()
        assertFalse(Span.current().spanContext.isValid)
    }

    @Test
    fun `auto-instrumented spans nest under the current omk span`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = true))

        val root = push(context, "Controller.get", MetricLayer.ENTRY)
        // Simulates what an OTel auto-instrumented client (JDBC, RestClient, ...) does:
        // start a span under the current context.
        val autoSpan = tracer.spanBuilder("jdbc.query").startSpan()
        autoSpan.end()
        root.end()
        context.pop()

        val exportedAuto = exporter.finishedSpanItems.first { it.name == "jdbc.query" }
        assertEquals(root.spanId, exportedAuto.parentSpanId)
        assertEquals(root.traceId, exportedAuto.traceId)
    }

    @Test
    fun `forked context keeps the parent trace id`() {
        val context = newContext(OtelSpanBridge(tracer, makeCurrent = false))
        val root = push(context, "Controller.get", MetricLayer.ENTRY)
        val adoptedTraceId = context.traceId

        val fork = context.forkAsync()
        assertEquals(adoptedTraceId, fork.traceId)

        fork.rootSpan!!.end()
        root.end()
        context.pop()
        assertTrue(exporter.finishedSpanItems.all { it.traceId == adoptedTraceId })

        val exportedFork = exporter.finishedSpanItems.first { it.name == "async.execution" }
        assertEquals(root.spanId, exportedFork.parentSpanId)
    }
}
