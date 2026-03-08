package io.github.hchanjune.omk.core.providers.metric

import io.github.hchanjune.omk.core.models.context.MetricsContext

/**
 * Enriches a finalized [io.github.hchanjune.omk.core.models.context.MetricsContext] before it is recorded.
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