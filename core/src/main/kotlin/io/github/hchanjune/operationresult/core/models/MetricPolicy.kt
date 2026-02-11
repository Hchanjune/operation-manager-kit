package io.github.hchanjune.operationresult.core.models

import java.util.Locale

/**
 * MetricPolicy defines safety rules for metric tags.
 *
 * ## Why This Exists
 * Metrics backends (Prometheus, Micrometer registries, etc.) store time-series
 * data based on the combination of:
 *
 * - metric name
 * - tag key/value pairs
 *
 * If tags are not controlled, high-cardinality explosions can occur
 * (e.g., userId, requestId, full paths), leading to excessive memory usage
 * and degraded performance.
 *
 * This policy provides a minimal set of guardrails:
 *
 * - Limit the total number of tags
 * - Limit tag value length
 * - Optionally restrict allowed tag keys
 * - Normalize tag values into a backend-friendly format
 *
 * ## Notes
 * This is intentionally lightweight and independent of any specific metrics backend.
 */
data class MetricPolicy(
    /**
     * Maximum number of tags allowed for a single metric.
     * Extra tags are dropped to ensure bounded series growth.
     */
    val maxTagCount: Int = 20,
    /**
     * Maximum length of a tag value.
     * Prevents accidental large/unbounded values from being stored.
     */
    val maxValueLength: Int = 80,
    /**
     * Optional allow-list of tag keys.
     * If provided, only keys in this set will be retained.
     */
    val allowedKeys: Set<String>? = null,
    /**
     * Fallback value used when a tag value is blank or invalid.
     */
    val unknown: String = "unknown"
) {
    /**
     * Normalizes and filters tags according to this policy.
     *
     * Steps:
     * 1. Apply key allow-list (if configured)
     * 2. Sanitize values (trim, lowercase, replace spaces)
     * 3. Drop blank values
     * 4. Enforce maximum tag count
     */
    fun normalize(tags: MetricTags): MetricTags {
        var entries = tags.values.entries.asSequence()

        if (allowedKeys != null) {
            entries = entries.filter { it.key in allowedKeys }
        }

        val normalized = entries
            .map { (k, v) -> k to sanitize(v) }
            .filter { (_, v) -> v.isNotBlank() }
            .take(maxTagCount)
            .toMap()

        return MetricTags.Builder(normalized.toMutableMap()).build()
    }

    /**
     * Sanitizes a raw tag value into a low-risk, backend-friendly form.
     *
     * - Trims whitespace
     * - Converts to lowercase
     * - Replaces spaces with underscores
     * - Enforces maximum length
     *
     * This helps ensure tag values remain consistent and low-cardinality.
     */
    private fun sanitize(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return unknown
        val lowered = s.lowercase(Locale.ROOT).replace(' ', '_')
        return if (lowered.length <= maxValueLength) lowered else lowered.substring(0, maxValueLength)
    }

    companion object {
        /**
         * Default policy suitable for most applications.
         */
        fun defaults(): MetricPolicy = MetricPolicy()
    }

}
