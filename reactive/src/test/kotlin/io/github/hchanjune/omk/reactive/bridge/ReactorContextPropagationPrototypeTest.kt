package io.github.hchanjune.omk.reactive.bridge

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 2(b) feasibility prototype — answers one question empirically before any production
 * wiring: if an OMK aspect stores the bridged span's OTel Context into the Reactor context via
 * [ContextPropagationOperator.storeOpenTelemetryContext], will an OTel-instrumented reactive
 * client (WebClient, R2DBC, ...) — which reads via
 * [ContextPropagationOperator.getOpenTelemetryContext] — parent its spans under the OMK span?
 *
 * The "instrumented client" here is simulated by reading the context exactly the way the
 * upstream instrumentation libraries do, including across operator boundaries and thread hops.
 */
class ReactorContextPropagationPrototypeTest {

    private val exporter = InMemorySpanExporter.create()
    private val tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
    private val tracer = tracerProvider.get("proto")

    @AfterTest
    fun tearDown() {
        tracerProvider.close()
    }

    /** Reads the OTel context the way instrumented clients do, then starts a "client" span under it. */
    private fun simulatedInstrumentedClient(name: String): Mono<String> =
        Mono.deferContextual { view ->
            val otelCtx = ContextPropagationOperator.getOpenTelemetryContextFromContextView(view, Context.current())
            val clientSpan = tracer.spanBuilder(name).setParent(otelCtx).startSpan()
            clientSpan.end()
            Mono.just("client-done")
        }

    @Test
    fun `client span parents under the span context stored via contextWrite`() {
        // Simulates what the OMK aspect would do: a live bridged span whose OTel Context
        // is written into the Reactor context for everything upstream in the pipeline.
        val omkSpan = tracer.spanBuilder("omk.operation").startSpan()
        val omkCtx = Context.root().with(omkSpan)

        simulatedInstrumentedClient("webclient.call")
            .contextWrite { ContextPropagationOperator.storeOpenTelemetryContext(it, omkCtx) }
            .block()
        omkSpan.end()

        val client = exporter.finishedSpanItems.first { it.name == "webclient.call" }
        assertEquals(omkSpan.spanContext.spanId, client.parentSpanId)
        assertEquals(omkSpan.spanContext.traceId, client.traceId)
    }

    @Test
    fun `stored context survives operator chains and thread hops`() {
        val omkSpan = tracer.spanBuilder("omk.operation").startSpan()
        val omkCtx = Context.root().with(omkSpan)

        Mono.just("start")
            .delayElement(Duration.ofMillis(20))                    // hop to parallel scheduler
            .flatMap { simulatedInstrumentedClient("r2dbc.query") } // read after the hop
            .map { it.uppercase() }
            .contextWrite { ContextPropagationOperator.storeOpenTelemetryContext(it, omkCtx) }
            .block()
        omkSpan.end()

        val client = exporter.finishedSpanItems.first { it.name == "r2dbc.query" }
        assertEquals(omkSpan.spanContext.spanId, client.parentSpanId)
    }

    @Test
    fun `nested contextWrite overrides outer one for the inner scope only`() {
        // Mirrors nested OMK aspects: repository span context must win inside the repository
        // pipeline while the controller span context stays in effect outside it.
        val controllerSpan = tracer.spanBuilder("omk.controller").startSpan()
        val controllerCtx = Context.root().with(controllerSpan)
        val repoSpan = tracer.spanBuilder("omk.repository")
            .setParent(controllerCtx)
            .startSpan()
        val repoCtx = controllerCtx.with(repoSpan)

        val inner = simulatedInstrumentedClient("inner.client")
            .contextWrite { ContextPropagationOperator.storeOpenTelemetryContext(it, repoCtx) }
        val outer = inner
            .flatMap { simulatedInstrumentedClient("outer.client") }
            .contextWrite { ContextPropagationOperator.storeOpenTelemetryContext(it, controllerCtx) }

        outer.block()
        repoSpan.end()
        controllerSpan.end()

        val innerClient = exporter.finishedSpanItems.first { it.name == "inner.client" }
        val outerClient = exporter.finishedSpanItems.first { it.name == "outer.client" }
        assertEquals(repoSpan.spanContext.spanId, innerClient.parentSpanId)
        assertEquals(controllerSpan.spanContext.spanId, outerClient.parentSpanId)
    }

    @Test
    fun `without stored context the client span is a disconnected root`() {
        val omkSpan = tracer.spanBuilder("omk.operation").startSpan()

        simulatedInstrumentedClient("webclient.call").block()
        omkSpan.end()

        val client = exporter.finishedSpanItems.first { it.name == "webclient.call" }
        assertEquals("0000000000000000", client.parentSpanId)
    }
}
