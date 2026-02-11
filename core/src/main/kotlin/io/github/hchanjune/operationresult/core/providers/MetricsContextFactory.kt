package io.github.hchanjune.operationresult.core.providers

import io.github.hchanjune.operationresult.core.models.MetricsContext

/**
 * Factory responsible for creating a new MetricsContext
 * at the beginning of an execution scope (request/operation).
 *
 * Implementations may enrich the context with environment-specific defaults.
 *
 * Example:
 * - webmvc module can decorate this factory to add HTTP tags (method, uri_template).
 */
fun interface MetricsContextFactory {
    /**
     * Creates a fresh MetricsContext for the current execution scope.
     */
    fun create(): MetricsContext
}