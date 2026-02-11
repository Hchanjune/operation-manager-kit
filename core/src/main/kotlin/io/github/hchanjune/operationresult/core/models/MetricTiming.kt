package io.github.hchanjune.operationresult.core.models

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
data class MetricTiming(
    /**
     * Start timestamp in epoch milliseconds.
     */
    val startedAtEpochMilli: Long? = null,
    /**
     * End timestamp in epoch milliseconds.
     */
    val endedAtEpochMilli: Long? = null,
) {
    /**
     * Marks the start time of this measurement scope.
     */
    fun start(clock: Clock): MetricTiming = copy(startedAtEpochMilli = clock.millis())

    /**
     * Marks the end time of this measurement scope.
     */
    fun end(clock: Clock): MetricTiming = copy(endedAtEpochMilli = clock.millis())

    /**
     * Returns the elapsed duration in milliseconds,
     * or null if the scope has not been completed.
     */
    fun durationMillis(): Long? =
        if (startedAtEpochMilli != null && endedAtEpochMilli != null) endedAtEpochMilli - startedAtEpochMilli else null

    /**
     * Creates a timing scope already started at the current time.
     */
    companion object {
        fun started(clock: Clock = Clock.systemUTC()): MetricTiming = MetricTiming(startedAtEpochMilli = clock.millis())
    }
}
