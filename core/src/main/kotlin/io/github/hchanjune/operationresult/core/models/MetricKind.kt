package io.github.hchanjune.operationresult.core.models

/**
 * MetricKind defines the high-level type of metric being recorded.
 *
 * ## Notes
 * - This abstraction is intentionally minimal and independent of Micrometer/Spring.
 * - Concrete metric backends (e.g., Micrometer Timer/Counter) can map these kinds accordingly.
 *
 * ## Common Usage
 * - TIMER:
 *   Used for measuring durations (latency, execution time).
 *
 * - COUNTER:
 *   Used for counting events (total calls, failures, rejections).
 */
enum class MetricKind {
    /**
     * Duration-based metric.
     * Typically recorded as a Timer/Histogram in the metrics backend.
     */
    TIMER,
    /**
     * Count-based metric.
     * Typically recorded as a monotonically increasing counter.
     */
    COUNTER
}