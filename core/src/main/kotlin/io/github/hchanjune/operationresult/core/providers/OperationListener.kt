package io.github.hchanjune.operationresult.core.providers

import io.github.hchanjune.operationresult.core.models.OperationContext

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
 * The provided [OperationContext] contains metadata about the execution,
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
     * @param context the operation execution context
     */
    fun onSuccess(context: OperationContext) {}
    /**
     * Called after an operation fails with an exception.
     *
     * @param context the operation execution context
     * @param exception the exception that caused the failure
     */
    fun onFailure(context: OperationContext, exception: Throwable) {}
}