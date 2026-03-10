package io.github.hchanjune.omk.webmvc.metrics

import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.micrometer.core.instrument.MeterRegistry

class ServletMetricsRecorder(
    private val registry: MeterRegistry
) : MetricsRecorder {

    override fun record(metricSpan: MetricSpan) {

    }
}