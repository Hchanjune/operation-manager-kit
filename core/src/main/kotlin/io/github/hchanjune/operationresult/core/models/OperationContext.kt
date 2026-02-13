package io.github.hchanjune.operationresult.core.models

import io.github.hchanjune.operationresult.core.providers.TelemetryContextProvider
import java.time.Instant

/**
 * Immutable metadata container describing a single operation execution.
 *
 * ## Purpose
 * [OperationContext] captures structured information about an operation,
 * including who executed it, where it originated, and how it completed.
 *
 * It is designed to support:
 * - Structured logging
 * - Audit trails
 * - Debugging and observability
 * - Metrics and monitoring integrations
 *
 * ## Lifecycle
 * A base context is created at the beginning of execution, and then typically
 * copied with additional fields (duration/response) once the operation completes.
 *
 * ## Core fields
 * - [correlationId]: Unique identifier tying together logs/events of this operation
 * - [issuer]: Actor identity responsible for the operation (user/system/anonymous)
 * - [entrypoint]: External entry location (e.g. controller method)
 * - [service] / [function]: Internal execution point (e.g. service class + method)
 * - [event]: Optional operation classification or event name
 *
 * ## Completion fields
 * - [message]: Optional human-readable summary
 * - [response]: Response or outcome summary (best-effort, not necessarily full payload)
 * - [durationMs]: Execution duration in milliseconds
 * - [timestamp]: Time when the operation context was created
 *
 * ## Attributes
 * [attributes] can carry additional custom key-value metadata.
 *
 * ## Notes
 * - Context instances are immutable; use `copy()` to enrich after execution.
 * - Avoid placing sensitive or excessively large data into [response] or [attributes].
 */
data class OperationContext(
    /** Unique identifier for correlating logs and traces within the same operation flow. */
    var correlationId: String,

    /** Identity of the actor (user/system) who initiated the operation. */
    val issuer: String,

    /** Entry point of the operation, typically a controller method signature. */
    val entrypoint: String,

    /** Service class name associated with the operation execution. */
    val service: String,

    /** Function or method name associated with the operation execution. */
    val function: String,

    /** Optional classification or operation name for the operation. */
    val operation: String = "",

    /** Optional classification or use case name for the operation. */
    val useCase: String = "",

    /** Optional classification or event name for the operation. */
    val event: String = "",

    /** Optional human-readable message describing the operation outcome. */
    var message: String = "",

    /** Best-effort textual representation of the operation response or result. */
    val response: String = "",

    /** Execution duration in milliseconds. Populated after completion. */
    val durationMs: Long = 0,

    /** Timestamp when this context was created. */
    val timestamp: Instant = Instant.now(),

    /** Additional custom metadata attributes for application-specific enrichment. */
    val attributes: Map<String, String> = emptyMap(),

    /**
     * Telemetry information (trace/span ids) for observability correlation.
     * Optional: present only when OpenTelemetry or tracing is enabled.
     */
    val telemetry: TelemetryContext
)