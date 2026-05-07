package io.github.hchanjune.omk.webmvc.metrics

import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricOutcome
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.util.concurrent.TimeUnit

class ServletMetricsRecorder(
    private val registry: MeterRegistry
) : MetricsRecorder {

    override fun record(metricSpan: MetricSpan) {
        val duration = metricSpan.durationMs ?: return
        val outcome = metricSpan.outcome ?: return
        val tags = buildTags(metricSpan, outcome)

        when (metricSpan.kind) {
            MetricKind.TIMER -> registry
                .timer("omk.span.duration", tags)
                .record(duration, TimeUnit.MILLISECONDS)
            MetricKind.COUNTER -> registry
                .counter("omk.span.count", tags)
                .increment()
        }
    }

    private fun buildTags(span: MetricSpan, outcome: MetricOutcome): Tags {
        var tags = Tags.empty()

        span.tags.values.forEach { (k, v) -> tags = tags.and(k, v) }

        tags = tags.and("status", outcome.status.name.lowercase())

        if (span.descriptor.useCase.isNotBlank()) {
            tags = tags.and("use_case", span.descriptor.useCase)
        }

        outcome.errorType?.let { tags = tags.and("error_type", it) }

        return tags
    }

}