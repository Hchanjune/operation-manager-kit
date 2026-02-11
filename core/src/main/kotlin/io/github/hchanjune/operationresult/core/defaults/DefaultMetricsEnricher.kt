package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.MetricsContext
import io.github.hchanjune.operationresult.core.providers.MetricsEnricher

object DefaultMetricsEnricher: MetricsEnricher {
    override fun enrich(context: MetricsContext) = context.withTags {
        put(MetricTagOption.RESULT, context.outcome?.result?.name?.lowercase())
    }
}