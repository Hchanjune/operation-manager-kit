package io.github.hchanjune.operationresult.core.providers

import io.github.hchanjune.operationresult.core.models.InvocationInfo

/**
 * Provides invocation context information for the current operation execution.
 *
 * ## Purpose
 * An operation is not only about success/failure, but also about *where* and *how*
 * it was triggered. [InvocationInfoProvider] supplies metadata describing the
 * current invocation point.
 *
 * This information is typically used for:
 * - Structured logging
 * - Audit trails ("who executed what, from where")
 * - Operation result enrichment
 * - Debugging and observability
 *
 * ## Example invocation data
 * An [InvocationInfo] may include:
 * - Entry point (e.g. controller method)
 * - Service and function names
 * - Event name or operation type
 * - Additional attributes (optional)
 *
 * ## Implementations
 * Implementations are environment-specific. For example:
 * - Web MVC integration may read values from MDC
 * - Reactive systems may use context propagation
 * - Custom systems may integrate with tracing frameworks
 *
 * ## Customization
 * Applications can override the default provider by defining their own
 * [InvocationInfoProvider] bean.
 *
 * If no custom provider is configured, the library may fall back to a default
 * implementation depending on the module in use (e.g. MDC-based provider).
 */
fun interface InvocationInfoProvider {
    /**
     * Returns the current invocation context information.
     *
     * This method should be lightweight and side-effect free.
     *
     * @return the current [InvocationInfo] describing the execution context.
     */
    fun current(): InvocationInfo
}