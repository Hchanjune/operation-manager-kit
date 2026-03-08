package io.github.hchanjune.omk.core.providers.operation

import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.models.context.OperationContext
import io.github.hchanjune.omk.core.models.context.TelemetryContext

/**
 * Lifecycle hooks for observing and extending operation execution.
 *
 * ## Purpose
 * [OperationListener] provides extension points that are invoked when an operation
 * completes successfully or fails with an exception.
 *
 * This allows applications to integrate cross-cutting concerns such as:
 * - Structured logging
 * - Audit trail recording
 * - Metrics collection (success/failure counts, latency, etc.)
 * - Notifications or monitoring integrations
 *
 * ## Hook semantics
 * - [onSuccess] is called when an operation finishes without throwing.
 * - [onFailure] is called when an operation fails due to an exception.
 *
 * The provided [io.github.hchanjune.omk.core.models.context.OperationContext] contains metadata about the execution,
 * including invocation info, issuer, correlation ID, and other attributes.
 *
 * ## Default behavior
 * All hook methods have empty default implementations, meaning hooks are optional.
 * If no custom hooks are provided, operation execution proceeds with no side effects.
 *
 * ## Guidelines for implementations
 * Implementations should be:
 * - Lightweight (avoid heavy blocking operations)
 * - Exception-safe (hooks should not throw)
 * - Side-effect aware (hooks run as part of the operation lifecycle)
 *
 * ## Customization
 * Applications can provide a custom [OperationListener] bean to override the default
 * no-op behavior.
 */
interface OperationListener {
    /**
     * Called after an operation completes successfully.
     *
     * @param operation the operation execution context
     */
    fun onSuccess(operation: OperationContext, metrics: MetricsContext, telemetry: TelemetryContext) {}
    /**
     * Called after an operation fails with an exception.
     *
     * @param operation the operation execution context
     * @param exception the exception that caused the failure
     */
    fun onFailure(operation: OperationContext, metrics: MetricsContext, telemetry: TelemetryContext, exception: Throwable) {}
}