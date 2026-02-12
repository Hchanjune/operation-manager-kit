package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.MetricOutcome
import io.github.hchanjune.operationresult.core.providers.MetricOutcomeClassifier

/**
 * Default implementation of [MetricOutcomeClassifier].
 *
 * ## Classification Rules
 * - If HTTP status code is available:
 *   - 2xx -> SUCCESS
 *   - 3xx -> SUCCESS (treated as non-failure by default)
 *   - 4xx -> REJECT
 *   - 5xx -> FAILURE
 *
 * - If HTTP status code is not available:
 *   - no exception -> SUCCESS
 *   - exception present -> FAILURE
 *
 * ## Exception Tag
 * - When an exception is present, the tag value is the exception's simple class name.
 * - Do NOT use exception messages or stack traces (high-cardinality).
 *
 * This default is intentionally conservative and backend-agnostic.
 * Web adapters may pass status codes; non-web adapters may classify based on exceptions only.
 */
object DefaultMetricOutcomeClassifier: MetricOutcomeClassifier {

    override fun classify(statusCode: Int?, error: Throwable?): MetricOutcome {
        val statusGroup = statusCode?.let(::toStatusGroup)

        val result = when {
            statusGroup == MetricOutcome.StatusGroup.S5XX -> MetricOutcome.Result.FAILURE
            statusGroup == MetricOutcome.StatusGroup.S4XX -> MetricOutcome.Result.REJECT
            statusGroup == MetricOutcome.StatusGroup.S2XX -> MetricOutcome.Result.SUCCESS
            statusGroup == MetricOutcome.StatusGroup.S3XX -> MetricOutcome.Result.SUCCESS

            // Non-HTTP execution or unknown status group
            error == null -> MetricOutcome.Result.SUCCESS
            else -> MetricOutcome.Result.FAILURE
        }

        val exception = error?.javaClass?.simpleName?: "none"

        return MetricOutcome(
            result = result,
            statusGroup = statusGroup,
            exception = exception
        )
    }

    private fun toStatusGroup(statusCode: Int): MetricOutcome.StatusGroup? =
        when (statusCode) {
            in 200..299 -> MetricOutcome.StatusGroup.S2XX
            in 300..399 -> MetricOutcome.StatusGroup.S3XX
            in 400..499 -> MetricOutcome.StatusGroup.S4XX
            in 500..599 -> MetricOutcome.StatusGroup.S5XX
            else -> null
        }
}