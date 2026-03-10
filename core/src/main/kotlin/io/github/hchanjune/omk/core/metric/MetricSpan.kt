package io.github.hchanjune.omk.core.metric

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
    private var timing: MetricTiming = MetricTiming.started(clock)

    var parent: MetricSpan? = null
        private set

    private val _children = mutableListOf<MetricSpan>()
    val children: List<MetricSpan> get() = _children

    val startTime: Long? get() = timing.startedAtEpochMilli

    val durationMs: Long? get() = timing.durationMillis()

    var outcome: MetricOutcome? = null
        private set

    fun addChild(child: MetricSpan) {
        check(child.parent == null) {
            "Span [${child.spanId}] already has a parent [${child.parent?.spanId}]"
        }
        if (_children.any { it.spanId == child.spanId }) return
        child.parent = this
        _children.add(child)
    }

    fun end() = finish(MetricOutcome.success())
    fun end(exception: Throwable) = finish(MetricOutcome.fail(exception))
    fun end(outcome: MetricOutcome) = finish(outcome)

    private fun finish(outcome: MetricOutcome) {
        this.timing = this.timing.end(clock)
        this.outcome = outcome
    }

}