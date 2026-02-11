package io.github.hchanjune.operationresult.core.models

/**
 * MetricName represents the stable identifier of a metric.
 *
 * ## Purpose
 * - Metrics backends (Prometheus, etc.) group and store measurements by metric name.
 * - Metric names MUST remain stable over time and should not be dynamically generated.
 *
 * ## Guidelines
 * - Use predefined, low-cardinality names such as:
 *   - "operation.duration"
 *   - "operation.calls"
 *   - "operation.failures"
 *
 * - Avoid embedding runtime values into the name (e.g., user IDs, request paths),
 *   as that would create unbounded metric series.
 *
 * ## Validation
 * - Names must not be blank.
 * - Names are length-limited to prevent accidental misuse.
 */
@JvmInline
value class MetricName(val value: String) {
    init {
        require(value.isNotBlank()) { "MetricName must not be blank" }
        require(value.length <= 200) { "MetricName length must be less than 200 characters" }
    }
}