package io.github.hchanjune.operationresult.core.models

/**
 * MetricOutcome represents the final classification of an execution.
 *
 * ## Purpose
 * Metrics are primarily used for aggregated operational monitoring.
 * Instead of tracking every individual failure in detail, metrics focus on
 * high-level outcomes that can be counted, alerted on, and visualized.
 *
 * ## Result Semantics
 * - SUCCESS:
 *   The execution completed normally (typically HTTP 2xx).
 *
 * - REJECT:
 *   The execution was rejected due to client-side or business constraints
 *   (validation errors, authorization failures, conflicts, etc.).
 *   These are usually NOT treated as operational incidents.
 *
 * - FAILURE:
 *   The execution failed due to server-side or infrastructural problems
 *   (timeouts, unexpected exceptions, HTTP 5xx).
 *   These are typically alert-worthy.
 *
 * ## Status Group
 * When running in an HTTP environment, responses may be grouped into coarse buckets:
 * - S2XX, S3XX, S4XX, S5XX
 *
 * This grouping helps keep tag cardinality low while still enabling meaningful analysis.
 *
 * ## Exception Tag
 * If an exception occurred, this field may contain a low-cardinality identifier,
 * such as a simple exception class name or a predefined error category.
 *
 * DO NOT store high-cardinality exception messages or stack traces here.
 * Those belong in invocation logs, not metrics.
 */
data class MetricOutcome(
    val result: Result,
    val statusGroup: StatusGroup? = null,
    val exception: String? = null
) {
    /**
     * High-level execution result classification.
     */
    enum class Result { SUCCESS, REJECT, FAILURE}
    /**
     * Coarse HTTP status group classification (optional).
     */
    enum class StatusGroup { S2XX, S3XX, S4XX, S5XX }
}
