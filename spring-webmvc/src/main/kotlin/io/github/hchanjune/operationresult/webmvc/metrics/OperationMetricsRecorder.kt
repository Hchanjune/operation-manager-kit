package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.defaults.MetricTagOption
import io.github.hchanjune.operationresult.core.models.MetricKind
import io.github.hchanjune.operationresult.core.models.MetricsContext
import io.github.hchanjune.operationresult.core.providers.MetricsRecorder
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

/**
 * Default Micrometer-backed implementation of [MetricsRecorder].
 *
 * Records finalized [MetricsContext] into the provided [MeterRegistry].
 */
class OperationMetricsRecorder(
    private val registry: MeterRegistry
): MetricsRecorder {

    override fun record(context: MetricsContext) {
        val outcome = context.outcome ?: return
        val durationMs = context.durationMillis() ?: return

        val enrichedTags = context.tags.toBuilder()
            .put(MetricTagOption.RESULT, outcome.result.name.lowercase())
            .put(MetricTagOption.STATUS_GROUP, outcome.statusGroup?.name?.lowercase())
            .put(MetricTagOption.EXCEPTION, outcome.exception)
            .build()

        val tags = enrichedTags.values
            .flatMap { (k, v) -> listOf(k, v) }
            .toTypedArray()

        when (context.kind) {
            MetricKind.TIMER -> {
                Timer.builder(context.name.value)
                    .tags(*tags)
                    .register(registry)
                    .record(Duration.ofMillis(durationMs))
            }

            MetricKind.COUNTER -> {
                registry.counter(context.name.value, *tags).increment()
            }
        }
    }


}