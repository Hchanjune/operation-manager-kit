package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: `[ENT]` ENTRY — opens a **root** span for a scheduler-triggered method and sets
 * `executionScope` to `SCHEDULED`. There is no inbound request or message to carry a trace
 * context, so a fresh `traceId`/`causationId` pair is generated for each run.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedSchedule(
    val description: String = "",
    /**
     * When true, the default success log is silenced (defaultLogging = false) if the method
     * returns an "empty" result: null, Unit, 0, false, or an empty Collection/Map/Array/CharSequence.
     * Useful for high-frequency pollers that mostly find nothing to do — return the processed
     * count (or the processed batch) so only meaningful runs are logged.
     * Failures are always logged regardless. Spans and metrics are recorded either way.
     */
    val quietWhenEmpty: Boolean = false
)
