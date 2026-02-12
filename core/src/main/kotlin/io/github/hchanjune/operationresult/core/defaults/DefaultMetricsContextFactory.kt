package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.MetricDescriptor
import io.github.hchanjune.operationresult.core.models.MetricKind
import io.github.hchanjune.operationresult.core.models.MetricName
import io.github.hchanjune.operationresult.core.models.MetricPolicy
import io.github.hchanjune.operationresult.core.models.MetricsContext
import io.github.hchanjune.operationresult.core.providers.MetricsContextFactory

/**
 * Default implementation of [MetricsContextFactory].
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
object DefaultMetricsContextFactory: MetricsContextFactory {

    /**
     * Default metric name used when no specific name is provided.
     *
     * Integration modules may override this behavior or wrap this factory.
     */
    private val defaultName: MetricName =
        MetricName("OperationManager")

    override fun create(): MetricsContext {
        return MetricsContext(
            name = defaultName,
            kind = MetricKind.TIMER,
            descriptor = MetricDescriptor(),
            policy = MetricPolicy.defaults()
        )
    }
}