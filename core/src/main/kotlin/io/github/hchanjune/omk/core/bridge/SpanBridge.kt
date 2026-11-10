package io.github.hchanjune.omk.core.bridge

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricTags
import java.time.Instant

/**
 * Seam for bridging OMK spans onto an external live tracing backend (e.g. OpenTelemetry).
 *
 * When a bridge is attached to the [io.github.hchanjune.omk.core.OperationRuntime], every
 * [ManagedContext.push] starts a real backend span and adopts the backend-generated span id
 * as the OMK span id, so the ids seen in OMK logs are the ids seen in the trace viewer.
 * Without a bridge, OMK generates its own ids and keeps its self-contained span tree —
 * behavior is unchanged.
 *
 * Implementations must be stateless and thread-safe; per-trace state travels in
 * [BridgedTrace]/[BridgedSpan] handles. Bridge failures are swallowed by the caller
 * (fail-open): tracing degrades, business flow never breaks.
 */
interface SpanBridge {

    /**
     * Called once per context, on the first span push. Builds the backend root context:
     * a remote parent when the context's trace was continued from an incoming request
     * ([ManagedContext.traceContinuedFromRemote] with backend-compatible ids), otherwise
     * a fresh root.
     */
    fun startTrace(context: ManagedContext): BridgedTrace

    /**
     * Starts a live backend span under [parent] (or under the trace root when null).
     * The returned handle carries the backend-generated ids; the caller adopts
     * [BridgedSpan.spanId] — and, for the root span, [BridgedSpan.traceId] — into OMK.
     */
    fun startSpan(
        trace: BridgedTrace,
        name: String,
        layer: MetricLayer,
        tags: MetricTags,
        parent: BridgedSpan?
    ): BridgedSpan

    /**
     * Ends the backend span for [handle], mapping the finished [span]'s outcome onto the
     * backend status. Called from [MetricSpan.end] — same thread as the start on servlet
     * stacks, possibly a different thread on reactive stacks.
     */
    fun endSpan(handle: BridgedSpan, span: MetricSpan)

    /**
     * Closes [handle]'s thread-bound current-context scope without ending the backend span, for a
     * span whose end is held back ([ManagedContext.holdBridgedSpans]): the scope must still close
     * in LIFO order on the thread that opened it.
     *
     * 끝내기를 미룬 span([ManagedContext.holdBridgedSpans])의 스레드 스코프만 닫고 백엔드 span 은 끝내지
     * 않는다. 스코프는 연 스레드에서 LIFO 순서로 닫혀야 하기 때문이다.
     */
    fun releaseScope(handle: BridgedSpan) {}

    /**
     * Sets an attribute on the live backend span only — it never becomes a metric tag. For values
     * that are known only after the span started (e.g. the matched HTTP route).
     *
     * 백엔드 span 에만 속성을 단다 — 메트릭 태그가 되지 않는다. span 을 연 뒤에야 알 수 있는 값용
     * (예: 매칭된 HTTP 경로).
     */
    fun setAttribute(handle: BridgedSpan, key: String, value: String) {}

    /**
     * Ends a held-back backend span at the moment it really finished. The scope was already closed
     * through [releaseScope].
     *
     * 미뤄 둔 백엔드 span 을 실제로 끝난 시각으로 끝낸다. 스코프는 [releaseScope] 에서 이미 닫혔다.
     */
    fun endSpan(handle: BridgedSpan, span: MetricSpan, endTime: Instant) = endSpan(handle, span)
}

/**
 * Backend root context for one OMK trace. [nativeContext] is backend-specific and only
 * interpreted by the [SpanBridge] that created it.
 */
class BridgedTrace(
    val nativeContext: Any
)

/**
 * Handle to one live backend span. [traceId]/[spanId] are the backend-generated ids
 * (adopted by OMK); [nativeSpan]/[nativeContext] are backend-specific and only interpreted
 * by the [SpanBridge] that created them. [nativeScope] is an optional thread-bound
 * current-context scope, closed by the bridge in [SpanBridge.endSpan].
 */
class BridgedSpan(
    val traceId: String,
    val spanId: String,
    val nativeSpan: Any,
    val nativeContext: Any,
    val nativeScope: AutoCloseable? = null
)
