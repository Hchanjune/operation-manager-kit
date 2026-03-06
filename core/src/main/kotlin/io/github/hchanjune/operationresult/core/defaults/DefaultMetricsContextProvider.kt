package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.metric.MetricDescriptor
import io.github.hchanjune.operationresult.core.models.metric.MetricKind
import io.github.hchanjune.operationresult.core.models.metric.MetricName
import io.github.hchanjune.operationresult.core.models.metric.MetricPolicy
import io.github.hchanjune.operationresult.core.models.context.MetricsContext
import io.github.hchanjune.operationresult.core.providers.metric.MetricsContextProvider

/**
 * Default implementation of [MetricsContextProvider].
 *
 * ## Purpose
 * Provides a minimal baseline MetricsContext in the core module.
 *
 * - No Spring or Micrometer dependencies
 * - No environment-specific tags
 * - Safe defaults for policy and timing
 *
 * Web adapters (webmvc/webflux) are expected to enrich tags such as:
 * - method
 * - uri_template
 * - status_group
 */
object DefaultMetricsContextProvider: MetricsContextProvider {

    /**
     * Default metric name used when no specific name is provided.
     *
     * Integration modules may override this behavior or wrap this factory.
     */
    private val defaultName: MetricName =
        MetricName("OperationManager")

    override fun current(): MetricsContext {
        return MetricsContext(
            name = defaultName,
            kind = MetricKind.TIMER,
            descriptor = MetricDescriptor(),
            policy = MetricPolicy.defaults()
        )
    }
}