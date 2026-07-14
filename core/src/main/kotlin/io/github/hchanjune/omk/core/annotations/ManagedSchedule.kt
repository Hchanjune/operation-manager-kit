package io.github.hchanjune.omk.core.annotations

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
