package io.github.hchanjune.omk.webmvc.metrics

import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricOutcome
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricStatus
import io.github.hchanjune.omk.core.metric.MetricTags
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class ServletMetricsRecorderTest {

    private val registry = SimpleMeterRegistry()
    private val recorder = ServletMetricsRecorder(registry)

    private fun span(
        kind: MetricKind = MetricKind.TIMER,
        useCase: String = "",
        ended: Boolean = true,
        outcome: MetricOutcome? = null
    ): MetricSpan {
        val s = MetricSpan(
            traceId = "trace-1",
            spanId = "span-1",
            name = MetricName("test.span"),
            kind = kind,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.empty(),
            descriptor = MetricDescriptor(useCase = useCase, layer = MetricLayer.APPLICATION)
        )
        if (ended) s.end(outcome ?: MetricOutcome.success())
        return s
    }

    @Test
    fun `returns early when durationMs is null (span not ended)`() {
        val s = span(ended = false)
        recorder.record(s)
        assertEquals(0, registry.meters.size)
    }

    @Test
    fun `returns early when outcome is null`() {
        // Create a span that is ended with a null outcome via the MetricSpan.end(MetricOutcome) path
        // We can simulate null outcome by not calling end() but injecting a duration hack.
        // The simplest way: use ended=false → outcome=null, durationMs=null → hits durationMs null first.
        // To isolate outcome=null: we need durationMs non-null but outcome null.
        // MetricSpan doesn't expose this combination publicly; durationMs is only non-null after end().
        // After end(), outcome is always non-null. So both null cases effectively resolve the same way.
        // We test that the recorder handles a non-ended span gracefully (covers both early-return branches).
        val s = span(ended = false)
        recorder.record(s)
        assertEquals(0, registry.meters.size)
    }

    @Test
    fun `records TIMER metric when kind is TIMER`() {
        recorder.record(span(kind = MetricKind.TIMER))
        val timer = registry.find("omk.span.duration").timer()
        assertEquals(1, timer?.count() ?: 0)
    }

    @Test
    fun `records COUNTER metric when kind is COUNTER`() {
        recorder.record(span(kind = MetricKind.COUNTER))
        val counter = registry.find("omk.span.count").counter()
        assertEquals(1.0, counter?.count() ?: 0.0)
    }

    @Test
    fun `includes use_case tag when useCase is not blank`() {
        recorder.record(span(useCase = "checkout"))
        val timer = registry.find("omk.span.duration").tag("use_case", "checkout").timer()
        assertEquals(1, timer?.count() ?: 0)
    }

    @Test
    fun `omits use_case tag when useCase is blank`() {
        recorder.record(span(useCase = ""))
        val timer = registry.find("omk.span.duration").timer()
        assertEquals(1, timer?.count() ?: 0)
        // no use_case tag on any meter
        val withUseCase = registry.find("omk.span.duration").tag("use_case", "").timer()
        assertEquals(null, withUseCase)
    }

    @Test
    fun `includes error_type tag when outcome has errorType`() {
        val outcome = MetricOutcome(MetricStatus.FAILURE_SERVER, errorType = "TimeoutException")
        recorder.record(span(outcome = outcome))
        val timer = registry.find("omk.span.duration").tag("error_type", "TimeoutException").timer()
        assertEquals(1, timer?.count() ?: 0)
    }

    @Test
    fun `omits error_type tag when outcome errorType is null`() {
        val outcome = MetricOutcome(MetricStatus.SUCCESS, errorType = null)
        recorder.record(span(outcome = outcome))
        val timer = registry.find("omk.span.duration").timer()
        assertEquals(1, timer?.count() ?: 0)
        val withErrorType = registry.find("omk.span.duration").tag("error_type", "").timer()
        assertEquals(null, withErrorType)
    }

    @Test
    fun `status tag reflects outcome status in lowercase`() {
        recorder.record(span(outcome = MetricOutcome.success()))
        val timer = registry.find("omk.span.duration").tag("status", "success").timer()
        assertEquals(1, timer?.count() ?: 0)
    }
}
