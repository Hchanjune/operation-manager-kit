package io.github.hchanjune.omk.reactive.support

import io.github.hchanjune.omk.core.metric.MetricSpan
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator
import reactor.core.publisher.Mono

/**
 * Writes a bridged span's OTel Context into the Reactor context, under the key that OTel's
 * reactive client instrumentations (WebClient, R2DBC, Lettuce, ...) read their parent from —
 * so their spans nest under the OMK span instead of starting disconnected root traces.
 * Verified empirically in ReactorContextPropagationPrototypeTest.
 *
 * `opentelemetry-instrumentation-reactor` is a compileOnly dependency: [BridgedReactorContext]
 * itself never touches OTel types and probes for the class once; the actual write lives in
 * [OtelContextWriter], which is only loaded (and only linked) when the probe succeeds.
 * No instrumentation library on the classpath → every call is a cheap no-op.
 */
internal object BridgedReactorContext {

    private val writer: OtelContextWriter? = runCatching {
        Class.forName("io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator")
        OtelContextWriter()
    }.getOrNull()

    fun <T : Any> propagate(mono: Mono<T>, span: MetricSpan): Mono<T> =
        writer?.write(mono, span) ?: mono
}

// Isolates the OTel/instrumentation references so BridgedReactorContext can be loaded
// without them on the classpath.
private class OtelContextWriter {
    fun <T : Any> write(mono: Mono<T>, span: MetricSpan): Mono<T> {
        val otelContext = span.bridgedContext as? Context ?: return mono
        return mono.contextWrite { ContextPropagationOperator.storeOpenTelemetryContext(it, otelContext) }
    }
}
