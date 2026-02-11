package io.github.hchanjune.operationresult.core.providers

import io.github.hchanjune.operationresult.core.models.MetricOutcome

/**
 * Classifies the final outcome of an execution into an aggregated metric result.
 *
 * This abstraction allows consistent operational semantics such as:
 * - SUCCESS  (2xx)
 * - REJECT   (4xx, validation/auth/business rejection)
 * - FAILURE  (5xx, timeouts, unexpected errors)
 *
 * Web adapters may provide HTTP status codes,
 * while non-web executions may rely only on exceptions.
 */
fun interface MetricOutcomeClassifier {
    /**
     * Classifies the execution outcome based on status code and/or exception.
     *
     * @param statusCode optional HTTP status code (if available)
     * @param error optional exception thrown during execution
     */
    fun classify(statusCode: Int?, error: Throwable?): MetricOutcome
}