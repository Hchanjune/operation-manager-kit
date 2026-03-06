package io.github.hchanjune.operationresult.core.providers.metric

import io.github.hchanjune.operationresult.core.models.context.MetricsContext

/**
 * Factory responsible for creating a new MetricsContext
 * at the beginning of an execution scope (request/operation).
 *
 * Implementations may enrich the context with environment-specific defaults.
 *
 * Example:
 * - webmvc module can decorate this factory to add HTTP tags (method, uri_template).
 */
fun interface MetricsContextProvider {
    /**
     * Creates a fresh MetricsContext for the current execution scope.
     */
    fun current(): MetricsContext
}