package io.github.hchanjune.operationresult.core.models

import java.time.Clock

/**
 * MetricsContext represents a single "measurement scope" for aggregated metrics.
 *
 * ## Purpose
 * - Captures metric-related data for one execution (e.g., one request or one operation).
 * - Intended to be recorded into time-series metric backends (Prometheus, etc.) via Micrometer adapters.
 *
 * ## Design Principles
 * - Lives in the `core` module and MUST NOT depend on Spring or Micrometer types.
 * - This is NOT an audit/trace record. For per-invocation tracking (correlationId, userId, payload, etc.),
 *   use an Invocation/Audit context and structured logs instead.
 * - Only low-cardinality information should be stored as tags.
 *   DO NOT put high-cardinality values (userId, requestId, correlationId, full path, query string) into tags.
 *
 * ## Extensibility
 * - `webmvc`/`webflux` modules may enrich this context with HTTP-specific tags
 *   (method, uri_template, status_group, etc.).
 * - A Micrometer integration module can translate this context into Timer/Counter updates.
 */
data class MetricsContext(
    /**
     * Metric name (should be stable and predefined).
     * Examples:
     * - "operation.duration"
     * - "operation.calls"
     */
    val name: MetricName,

    /**
     * Metric kind. Start with TIMER/COUTNER and expand as needed.
     */
    val kind: MetricKind = MetricKind.TIMER,

    /**
     * Metric tags (labels) used for grouping/aggregation in the metrics backend.
     * Tags must remain low-cardinality.
     */
    val tags: MetricTags = MetricTags.empty(),

    /**
     * Timing information for duration measurements.
     * Typically starts at the beginning of an execution scope and ends when the scope completes.
     */
    val timing: MetricTiming = MetricTiming.started(),

    /**
     * Outcome of the execution (resolved at the end of the scope).
     * Typically distinguishes operational success/reject/failure semantics.
     */
    val outcome: MetricOutcome? = null,

    /**
     * Safety policy for tags.
     * Used to prevent cardinality explosions by limiting tag count/value length and normalizing values.
     */
    val policy: MetricPolicy = MetricPolicy.defaults()
) {

    /**
     * Adds/updates tags and normalizes them using the configured policy.
     * Intended for adapters (e.g., webmvc/webflux) to enrich tags safely.
     */
    fun withTags(block: MetricTags.Builder.() -> Unit): MetricsContext =
        copy(tags = tags.toBuilder().apply(block).build().let(policy::normalize))

    /** Marks the start timestamp for this measurement scope. */
    fun start(clock: Clock = Clock.systemUTC()): MetricsContext =
        copy(timing = timing.start(clock))

    /**
     * Marks the end timestamp and finalizes the outcome.
     * After this, a recorder can persist the measurement (e.g., update a Timer).
     */
    fun end(clock: Clock = Clock.systemUTC(), outcome: MetricOutcome): MetricsContext =
        copy(timing = timing.end(clock), outcome = outcome)

    /** Returns the measured duration in milliseconds, if start/end are both present. */
    fun durationMillis(): Long? = timing.durationMillis()
}
