package io.github.hchanjune.operationresult.core.models

/**
 * MetricTags represents the final set of low-cardinality labels attached to a metric.
 *
 * ## Purpose
 * Tags (also known as labels) are used by metrics backends to group and aggregate
 * time-series measurements.
 *
 * Example (Prometheus-style):
 *
 *   http.server.requests{method="GET", uri="/trees/{id}", status="500"}
 *
 * In this example:
 * - "http.server.requests" is the metric name
 * - method/uri/status are tags
 *
 * ## Cardinality Rules
 * Tags MUST remain low-cardinality.
 * Do not store unbounded values such as:
 * - userId
 * - requestId
 * - correlationId
 * - full request paths (/trees/12345)
 * - query strings
 *
 * Those belong in invocation logs, not metrics.
 *
 * ## Builder Usage
 * Tags are built incrementally via the Builder API, allowing adapters
 * (webmvc/webflux, etc.) to enrich tags safely before recording.
 */
data class MetricTags private constructor(
    /**
     * Underlying tag key/value map.
     */
    val values: Map<String, String>
) {
    /**
     * Creates a mutable builder initialized with the current tag set.
     */
    fun toBuilder(): Builder = Builder(values.toMutableMap())

    /**
     * Builder for constructing MetricTags safely and incrementally.
     */
    class Builder(private val map: MutableMap<String, String> = linkedMapOf()) {
        /**
         * Adds or replaces a tag entry.
         *
         * Blank values are ignored to avoid meaningless series.
         */
        fun put(key: String, value: String?): Builder {
            if (!value.isNullOrBlank()) map[key] = value
            return this
        }

        /**
         * Merges all tags from another MetricTags instance.
         */
        fun putAll(other: MetricTags): Builder {
            map.putAll(other.values)
            return this
        }

        /**
         * Builds an immutable MetricTags instance.
         */
        fun build(): MetricTags = MetricTags(map.toMap())

    }

    companion object {
        /**
         * Returns an empty tag set.
         */
        fun empty(): MetricTags = MetricTags(emptyMap())
    }

}
