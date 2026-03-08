package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.defaults.MetricTagOption
import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.providers.metric.MetricsEnricher

object DefaultMetricsEnricher: MetricsEnricher {
    override fun enrich(context: MetricsContext) = context.withTags {
        put(MetricTagOption.RESULT, context.outcome?.result?.name?.lowercase())
    }
}