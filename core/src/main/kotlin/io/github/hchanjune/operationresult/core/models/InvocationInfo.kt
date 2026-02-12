package io.github.hchanjune.operationresult.core.models

/**
 * Describes the current invocation point of an operation.
 *
 * ## Purpose
 * [InvocationInfo] represents *where* an operation was triggered and executed.
 * It is typically provided by an [InvocationInfoProvider] implementation and
 * used to populate the initial [OperationContext].
 *
 * This information enables:
 * - Clear identification of entrypoints and execution layers
 * - Structured logging and tracing
 * - Audit-friendly operation metadata
 *
 * ## Field meanings
 * - [entrypoint]: External entry location (e.g. controller method)
 * - [service]: Service class name associated with execution
 * - [function]: Function or method name being executed
 * - [useCase]: Optional operation classification or use case label (domain-driven)
 * - [event]: Optional operation classification or event label (event-driven)
 * - [attributes]: Additional custom metadata
 *
 * ## Defaults
 * All fields provide safe fallback values ("Unknown*") to ensure that invocation
 * metadata remains stable even when no integration (e.g. MDC, AOP) is present.
 *
 * ## Notes
 * This is a lightweight immutable data carrier. It should not contain sensitive
 * or excessively large payloads.
 */
data class InvocationInfo(
    val entrypoint: String = "UnknownEntry",
    val service: String = "UnknownService",
    val function: String = "UnknownFunction",
    val operation: String = "UnknownOperation",
    val useCase: String = "UnknownCase",
    val event: String = "UnknownEvent",
    val attributes: Map<String, String> = emptyMap(),
)