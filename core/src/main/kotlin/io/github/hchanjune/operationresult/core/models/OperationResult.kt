package io.github.hchanjune.operationresult.core.models

/**
 * Represents the outcome of an operation execution.
 *
 * ## Purpose
 * [OperationResult] bundles together:
 * - The produced business result ([data])
 * - The associated execution metadata ([context])
 *
 * This allows applications to return or log results with consistent
 * operational context such as correlation ID, issuer, entrypoint, and duration.
 *
 * ## Usage
 * ```kotlin
 * val result: OperationResult<User> = Operation {
 *     findUser()
 * }
 *
 * println(result.context.correlationId)
 * println(result.data)
 * ```
 *
 * ## Notes
 * - Exceptions are not wrapped inside [OperationResult].
 *   If the operation fails, the executor rethrows the exception instead.
 * - The context is immutable and reflects the completed operation snapshot.
 *
 * @param T the type of the operation result data
 * @property context execution metadata describing this operation
 * @property data the business result produced by the operation
 */
data class OperationResult<T>(
    val context: OperationContext,
    val metrics: MetricsContext,
    val data: T
)