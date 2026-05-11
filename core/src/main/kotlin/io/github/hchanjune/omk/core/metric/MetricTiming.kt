package io.github.hchanjune.omk.core.metric

import java.time.Clock

/**
 * MetricTiming captures the start/end timestamps of a measurement scope.
 *
 * ## Purpose
 * Metrics commonly record execution latency (duration).
 * This type provides a backend-agnostic way to measure elapsed time
 * without depending on Micrometer or Spring infrastructure.
 *
 * ## Usage
 * - Created at the beginning of an operation/request via [started]
 * - Completed at the end via [end]
 * - Duration can then be computed using [durationMillis]
 *
 * ## Notes
 * - This implementation uses epoch milliseconds for simplicity.
 * - More precise time sources (e.g., nanoTime) can be introduced later if needed.
 */
class MetricTiming(clock: Clock) {
    val startedAtEpochMilli: Long = clock.millis()
    private var endedAtEpochMilli: Long? = null

    fun end(clock: Clock) {
        if (endedAtEpochMilli != null) return
        endedAtEpochMilli = clock.millis()
    }

    fun durationMillis(): Long? =
        endedAtEpochMilli?.let { it - startedAtEpochMilli }

    companion object {
        fun started(clock: Clock = Clock.systemUTC()): MetricTiming = MetricTiming(clock)
    }
}