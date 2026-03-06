package io.github.hchanjune.operationresult.core.providers.metric

import io.github.hchanjune.operationresult.core.models.context.MetricsContext

/**
 * Enriches a finalized [MetricsContext] before it is recorded.
 *
 * This allows adapters (WebMVC, WebFlux, etc.) to attach
 * additional low-cardinality tags such as:
 * - result (success/failure)
 * - http.method / http.route
 * - exception category
 *
 * Core does not impose tag policies directly.
 */
fun interface MetricsEnricher {
    fun enrich(context: MetricsContext): MetricsContext
}