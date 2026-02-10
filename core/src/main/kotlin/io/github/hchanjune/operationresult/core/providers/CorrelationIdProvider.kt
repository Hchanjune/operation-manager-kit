package io.github.hchanjune.operationresult.core.providers

/**
 * Provides correlation IDs for operation execution.
 *
 * ## What is a correlation ID?
 * A correlation ID is a unique identifier used to group together all logs,
 * events, and operation results produced within the same execution flow.
 *
 * It enables end-to-end tracing across layers such as:
 * - Controller entrypoints
 * - Service method calls
 * - Persistence operations
 * - External API calls
 *
 * ## Usage
 * The correlation ID is typically generated at the start of an operation and
 * stored in the operation context and/or MDC for logging.
 *
 * ## Customization
 * Applications may provide their own implementation to integrate with:
 * - Incoming request IDs (e.g. `X-Request-Id`)
 * - Distributed tracing systems (e.g. OpenTelemetry trace/span IDs)
 * - Domain-specific identifiers
 *
 * ## Default implementation
 * If no custom provider is configured, the library uses a UUID-based implementation
 * (see `DefaultCorrelationIdProvider`).
 */
fun interface CorrelationIdProvider {
    /**
     * Generates a new correlation ID.
     *
     * @return a unique identifier representing the current operation flow.
     */
    fun newCorrelationId(): String
}