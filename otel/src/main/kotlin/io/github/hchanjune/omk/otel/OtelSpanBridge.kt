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
            applyCacheHit(otelSpan, span)
            applyStatus(otelSpan, span)
            otelSpan.end()
        }
    }

    /**
     * Cache hit/miss travels as an **attribute**, never as span status.
     *
     * OTel span status is only `UNSET`/`OK`/`ERROR` and means "did this operation fail" — a cache
     * miss is a normal outcome, so reporting it as ERROR would make dashboard error rates swing
     * with the hit ratio. As an attribute the two axes stay independent: filter on `cache.hit` for
     * the hit ratio, on span status for real failures (e.g. a Redis timeout).
     *
     * Set at end time rather than at [startSpan], because whether the lookup hit is only known
     * after the cache has been consulted.
     *
     * 캐시 히트/미스는 스팬 상태가 아니라 **attribute** 로 보낸다.
     *
     * OTel 스팬 상태는 `UNSET`/`OK`/`ERROR` 뿐이고 "이 작업이 실패했나"만 뜻한다. 캐시 미스는 정상
     * 결과라서 ERROR 로 보내면 대시보드 에러율이 적중률에 따라 출렁인다. attribute 로 두면 두 축이
     * 독립적으로 남는다 — 적중률은 `cache.hit` 으로, 진짜 실패(Redis 타임아웃 등)는 스팬 상태로 본다.
     *
     * [startSpan] 이 아니라 종료 시점에 넣는 이유는, 히트 여부가 캐시를 조회해봐야 정해지기 때문이다.
     */
    private fun applyCacheHit(otelSpan: Span, metricSpan: MetricSpan) {
        val hit = metricSpan.cacheHit ?: return
        otelSpan.setAttribute(CACHE_HIT_ATTRIBUTE, hit)
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

    companion object {
        /**
         * Attribute name for the cache hit flag. Keep it stable — dashboards filter on it.
         * 캐시 히트 플래그의 attribute 이름. 대시보드가 이걸로 필터하므로 바꾸지 않는다.
         */
        private const val CACHE_HIT_ATTRIBUTE = "cache.hit"
    }
}
