package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.context.MetricsContext
import io.github.hchanjune.operationresult.core.providers.metric.MetricsEnricher

object DefaultMetricsEnricher: MetricsEnricher {
    override fun enrich(context: MetricsContext) = context.withTags {
        put(MetricTagOption.RESULT, context.outcome?.result?.name?.lowercase())
    }
}