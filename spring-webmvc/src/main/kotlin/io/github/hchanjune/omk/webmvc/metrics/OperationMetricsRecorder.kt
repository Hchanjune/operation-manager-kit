package io.github.hchanjune.omk.webmvc.metrics

import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.models.metric.MetricKind
import io.github.hchanjune.omk.core.providers.metric.MetricsRecorder
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.time.Duration

/**
 * Default Micrometer-backed implementation of [io.github.hchanjune.omk.core.providers.metric.MetricsRecorder].
 *
 * Records finalized [io.github.hchanjune.omk.core.models.context.MetricsContext] into the provided [io.micrometer.core.instrument.MeterRegistry].
 */
class OperationMetricsRecorder(
    private val registry: MeterRegistry
) : MetricsRecorder {

    override fun record(context: MetricsContext) {
        val outcome = context.outcome ?: return
        val durationMs = context.durationMillis() ?: return

        val micrometerTags = context.tags.values.map { (k, v) ->
            Tag.of(k, v)
        }

        val metricName = context.name.value

        when (context.kind) {
            MetricKind.TIMER -> {
                Timer.builder(metricName)
                    .tags(micrometerTags)
                    .register(registry)
                    .record(Duration.ofMillis(durationMs))
            }

            MetricKind.COUNTER -> {
                registry.counter(metricName, micrometerTags).increment()
            }
        }
    }
}