package io.github.hchanjune.omk.reactive.metrics

import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricOutcome
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import java.util.concurrent.TimeUnit

class ReactiveMetricsRecorder(
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
        val normalizedTags = span.policy.normalize(span.tags)
        val list = ArrayList<Tag>(normalizedTags.values.size + 3)
        normalizedTags.values.forEach { (k, v) -> list.add(Tag.of(k, v)) }
        list.add(Tag.of("status", outcome.status.name.lowercase()))
        if (span.descriptor.useCase.isNotBlank()) list.add(Tag.of("use_case", span.descriptor.useCase))
        outcome.errorType?.let { list.add(Tag.of("error_type", it)) }
        return Tags.of(list)
    }
}
