package io.github.hchanjune.operationresult.core

import io.github.hchanjune.operationresult.core.models.OperationContext
import io.github.hchanjune.operationresult.core.models.OperationResult
import io.github.hchanjune.operationresult.core.providers.CorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.OperationHooks

/**
 * Core executor responsible for running an operation and producing an [OperationResult].
 *
 * ## Purpose
 * [OperationExecutor] provides a structured execution boundary around business logic,
 * capturing consistent metadata such as:
 * - Correlation ID (execution trace identifier)
 * - Issuer (actor identity)
 * - Invocation context (entrypoint/service/function)
 * - Execution duration
 *
 * It also triggers lifecycle hooks for success/failure handling.
 *
 * ## Execution flow
 * 1. Resolve invocation metadata via [InvocationInfoProvider]
 * 2. Generate correlation ID via [CorrelationIdProvider]
 * 3. Resolve issuer identity via [IssuerProvider]
 * 4. Execute the user-provided [block] with an initialized [OperationContext]
 * 5. Record execution duration and response summary
 * 6. Invoke [OperationHooks] callbacks
 * 7. Return an [OperationResult] on success, or rethrow exceptions on failure
 *
 * ## Exception handling
 * This executor does **not** swallow exceptions.
 * If the operation block throws, [onFailure] is invoked and the exception is rethrown.
 *
 * ## Notes
 * - The response stored in [OperationContext.response] is currently derived from
 *   `result.toString()` or exception type name. Applications may customize this behavior
 *   by extending hooks or wrapping results differently.
 * - Implementations of hooks should be lightweight and exception-safe.
 *
 * ## Usage example
 * ```kotlin
 * val result = executor.run {
 *     // business logic here
 *     "OK"
 * }
 * ```
 */
class OperationExecutor(
    private val invocationInfoProvider: InvocationInfoProvider,
    private val issuerProvider: IssuerProvider,
    private val correlationIdProvider: CorrelationIdProvider,
    private val hooks: OperationHooks
) {

    /**
     * Executes the given [block] as an operation.
     *
     * The block receives an [OperationContext] containing invocation metadata.
     *
     * @param block operation logic to execute
     * @return an [OperationResult] containing both context and produced data
     * @throws Throwable rethrows any exception thrown by the operation block
     */
    fun <T> run(
        block: OperationContext.() -> T
    ): OperationResult<T> {
        val info = invocationInfoProvider.current()

        // Base operation context created before execution begins.
        val baseCtx = OperationContext(
            correlationId = correlationIdProvider.newCorrelationId(),
            issuer = issuerProvider.currentIssuer(),
            entrypoint = info.entrypoint,
            service = info.service,
            function = info.function,
            event = info.event,
            attributes = info.attributes,
        )

        val start = System.nanoTime()

        return try {
            // Execute user business logic.
            val result = baseCtx.block()

            // Successful completion context.
            val successCtx = baseCtx.copy(
                durationMs = (System.nanoTime() - start) / 1_000_000,
                response = result.toString()
            )

            hooks.onSuccess(successCtx)

            OperationResult(
                context = successCtx,
                data = result
            )
        } catch (exception: Throwable) {
            // Failure completion context.
            val failureCtx = baseCtx.copy(
                durationMs = (System.nanoTime() - start) / 1_000_000,
                response = exception::class.simpleName ?: "Exception"
            )
            hooks.onFailure(failureCtx, exception)

            // Exceptions are not swallowed.
            throw exception
        }
    }

    /**
     * Operator shortcut for [run].
     *
     * Allows execution via:
     * ```kotlin
     * executor {
     *     ...
     * }
     * ```
     */
    operator fun <T> invoke(block: OperationContext.() -> T): OperationResult<T> = run(block)

}