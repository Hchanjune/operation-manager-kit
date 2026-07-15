package io.github.hchanjune.omk.core.bridge

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricTags

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
