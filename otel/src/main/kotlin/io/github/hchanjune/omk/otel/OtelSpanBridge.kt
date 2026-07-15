package io.github.hchanjune.omk.otel

import io.github.hchanjune.omk.core.bridge.BridgedSpan
import io.github.hchanjune.omk.core.bridge.BridgedTrace
import io.github.hchanjune.omk.core.bridge.SpanBridge
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricStatus
import io.github.hchanjune.omk.core.metric.MetricTags
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

/**
 * Live OpenTelemetry [SpanBridge]: every OMK span push starts a real OTel span, so the
 * OTel-generated ids are the OMK ids — logs and trace viewers agree — and, with
 * [makeCurrent], OTel auto-instrumentation (JDBC, HTTP clients, ...) nests under OMK spans
 * and outbound context propagation carries real span ids.
 *
 * @param makeCurrent open a thread-bound current-context scope per span. Only safe on
 * stacks where a span starts and ends on the same thread in LIFO order (servlet aspects).
 * Reactive stacks must pass false and propagate context through the Reactor chain instead.
 */
class OtelSpanBridge(
    private val tracer: Tracer,
    private val makeCurrent: Boolean
) : SpanBridge {

    override fun startTrace(context: ManagedContext): BridgedTrace {
        if (context.traceContinuedFromRemote) {
            val traceId = context.traceId
            val causationId = context.causationId
            if (traceId.length == 32 && causationId.length == 16) {
                runCatching {
                    val remote = SpanContext.createFromRemoteParent(
                        traceId, causationId,
                        TraceFlags.getSampled(),
                        TraceState.getDefault()
                    )
                    if (remote.isValid) return BridgedTrace(Context.root().with(Span.wrap(remote)))
                }
            }
        }
        return BridgedTrace(Context.root())
    }

    override fun startSpan(
        trace: BridgedTrace,
        name: String,
        layer: MetricLayer,
        tags: MetricTags,
        parent: BridgedSpan?
    ): BridgedSpan {
        val parentCtx = (parent?.nativeContext ?: trace.nativeContext) as Context

        val otelSpan = tracer.spanBuilder(name)
            .setParent(parentCtx)
            .setSpanKind(layer.toSpanKind())
            .startSpan()
        tags.values.forEach { (k, v) -> otelSpan.setAttribute(k, v) }

        val spanCtx = parentCtx.with(otelSpan)
        val scope = if (makeCurrent) spanCtx.makeCurrent() else null

        return BridgedSpan(
            traceId = otelSpan.spanContext.traceId,
            spanId = otelSpan.spanContext.spanId,
            nativeSpan = otelSpan,
            nativeContext = spanCtx,
            nativeScope = scope
        )
    }

    override fun endSpan(handle: BridgedSpan, span: MetricSpan) {
        try {
            handle.nativeScope?.close()
        } finally {
            val otelSpan = handle.nativeSpan as Span
            applyStatus(otelSpan, span)
            otelSpan.end()
        }
    }

    private fun applyStatus(otelSpan: Span, metricSpan: MetricSpan) {
        val outcome = metricSpan.outcome ?: return
        when (outcome.status) {
            MetricStatus.SUCCESS -> otelSpan.setStatus(StatusCode.OK)
            else -> {
                val desc = listOfNotNull(outcome.errorType, outcome.errorMessage)
                    .joinToString(": ")
                    .ifBlank { outcome.status.name }
                otelSpan.setStatus(StatusCode.ERROR, desc)
            }
        }
    }

    private fun MetricLayer.toSpanKind(): SpanKind = when (this) {
        MetricLayer.ENTRY       -> SpanKind.SERVER
        MetricLayer.APPLICATION -> SpanKind.INTERNAL
        MetricLayer.DB          -> SpanKind.CLIENT
        MetricLayer.CACHE       -> SpanKind.CLIENT
        MetricLayer.EXTERNAL    -> SpanKind.CLIENT
    }
}
