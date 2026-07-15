package io.github.hchanjune.omk.core.metric

import io.github.hchanjune.omk.core.bridge.BridgedSpan
import io.github.hchanjune.omk.core.bridge.SpanBridge
import org.slf4j.LoggerFactory
import java.time.Clock

class MetricSpan(
    val traceId: String,
    val spanId: String,
    val name: MetricName,
    val kind: MetricKind,
    val policy: MetricPolicy,
    val tags: MetricTags,
    val descriptor: MetricDescriptor,
    private val clock: Clock = Clock.systemUTC()
) {
    val threadName: String = Thread.currentThread().name
    private val timing: MetricTiming = MetricTiming.started(clock)

    var parent: MetricSpan? = null
        private set

    private val _children = mutableListOf<MetricSpan>()
    private val _childSpanIds = HashSet<String>()
    val children: List<MetricSpan> get() = _children

    val startTime: Long? get() = timing.startedAtEpochMilli

    val durationMs: Long? get() = timing.durationMillis()

    var outcome: MetricOutcome? = null
        private set

    // Live backend span this OMK span is bridged onto; set by ManagedContext.push.
    internal var bridge: SpanBridge? = null
    internal var bridgeHandle: BridgedSpan? = null

    /**
     * The backend-native context of the bridged live span (e.g. an OTel Context), or null when
     * no bridge is active. Stack adapters use it to propagate the span as the backend's current
     * context (reactive: written into the Reactor context so instrumented clients nest under it).
     */
    val bridgedContext: Any? get() = bridgeHandle?.nativeContext

    fun addChild(child: MetricSpan) {
        check(child.parent == null) {
            "Span [${child.spanId}] already has a parent [${child.parent?.spanId}]"
        }
        if (!_childSpanIds.add(child.spanId)) return
        child.parent = this
        _children.add(child)
    }

    fun end() = finish(MetricOutcome.success())
    fun end(exception: Throwable) = finish(MetricOutcome.fail(exception))
    fun end(outcome: MetricOutcome) = finish(outcome)

    private fun finish(outcome: MetricOutcome) {
        this.timing.end(clock)
        this.outcome = outcome
        val handle = bridgeHandle ?: return
        try {
            bridge?.endSpan(handle, this)
        } catch (e: Throwable) {
            log.warn("Span bridge failed to end span [{}]: {}", spanId, e.toString())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetricSpan::class.java)
    }
}