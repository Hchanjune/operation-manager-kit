package io.github.hchanjune.omk.servlet.hooks

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricStatus
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.springframework.core.annotation.Order
import java.util.concurrent.TimeUnit

@Order(70)
class OtelOperationHook(private val tracer: Tracer) : OperationHook {

    override fun onSuccess(context: ManagedContext) = exportSpans(context)
    override fun onFailure(context: ManagedContext, exception: Throwable) = exportSpans(context)

    private fun exportSpans(context: ManagedContext) {
        val root = context.rootSpan ?: return
        export(root, buildRemoteParentContext(context))
    }

    private fun buildRemoteParentContext(context: ManagedContext): Context {
        val traceId = context.traceId
        val causationId = context.causationId
        if (traceId.length == 32 && causationId.length == 16) {
            runCatching {
                val remote = SpanContext.createFromRemoteParent(
                    traceId, causationId,
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
                )
                if (remote.isValid) return Context.root().with(Span.wrap(remote))
            }
        }
        return Context.root()
    }

    private fun export(metricSpan: MetricSpan, parentCtx: Context) {
        val startMs = metricSpan.startTime ?: return

        val otelSpan = tracer.spanBuilder(metricSpan.name.value)
            .setParent(parentCtx)
            .setSpanKind(metricSpan.descriptor.layer.toSpanKind())
            .setStartTimestamp(startMs, TimeUnit.MILLISECONDS)
            .startSpan()

        metricSpan.tags.values.forEach { (k, v) -> otelSpan.setAttribute(k, v) }
        applyStatus(otelSpan, metricSpan)

        val childCtx = parentCtx.with(otelSpan)
        metricSpan.children.forEach { child -> export(child, childCtx) }

        val endMs = metricSpan.durationMs?.let { startMs + it } ?: System.currentTimeMillis()
        otelSpan.end(endMs, TimeUnit.MILLISECONDS)
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
