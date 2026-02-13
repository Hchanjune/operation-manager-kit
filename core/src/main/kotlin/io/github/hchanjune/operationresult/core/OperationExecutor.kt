package io.github.hchanjune.operationresult.core

import io.github.hchanjune.operationresult.core.models.MetricDescriptor
import io.github.hchanjune.operationresult.core.models.OperationContext
import io.github.hchanjune.operationresult.core.models.OperationResult
import io.github.hchanjune.operationresult.core.providers.CorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.MetricOutcomeClassifier
import io.github.hchanjune.operationresult.core.providers.MetricsContextFactory
import io.github.hchanjune.operationresult.core.providers.MetricsEnricher
import io.github.hchanjune.operationresult.core.providers.MetricsRecorder
import io.github.hchanjune.operationresult.core.providers.OperationListener
import io.github.hchanjune.operationresult.core.providers.TelemetryContextProvider

/**
 * Core executor responsible for running an operation and producing an [OperationResult].
 *
 * ## Purpose
 * [OperationExecutor] defines a consistent execution boundary around business logic.
 *
 * It captures two complementary concerns:
 *
 * ### 1. Invocation Metadata (per-invocation tracing)
 * - Correlation ID (execution trace identifier)
 * - Issuer (actor identity)
 * - Invocation context (entrypoint/service/function)
 *
 * These values are primarily used for structured logging and audit-style tracing.
 *
 * ### 2. Aggregated Metrics (operational monitoring)
 * - MetricsContext lifecycle (start/end)
 * - Outcome classification (success/reject/failure)
 * - Recording into an external metrics backend via [MetricsRecorder]
 *
 * Metrics are intended for low-cardinality aggregation in time-series systems
 * such as Prometheus (via Micrometer integrations).
 *
 * ## Execution flow
 * 1. Resolve invocation metadata via [InvocationInfoProvider]
 * 2. Generate correlation ID via [CorrelationIdProvider]
 * 3. Resolve issuer identity via [IssuerProvider]
 * 4. Create and start a backend-agnostic metrics scope via [MetricsContextFactory]
 * 5. Execute the user-provided [block] with an initialized [OperationContext]
 * 6. Finalize execution duration and response summary
 * 7. Classify the execution outcome via [MetricOutcomeClassifier]
 * 8. Record the finalized metrics via [MetricsRecorder]
 * 9. Invoke lifecycle callbacks via [OperationListener]
 * 10. Return an [OperationResult] on success, or rethrow exceptions on failure
 *
 * ## Exception handling
 * This executor does **not** swallow exceptions.
 * If the operation block throws, [OperationListener.onFailure] is invoked,
 * metrics are finalized as failure, and the exception is rethrown.
 *
 * ## Notes
 * - Metrics tags MUST remain low-cardinality. Do not include values such as
 *   userId, requestId, correlationId, or full request paths.
 *   Those belong in invocation logs, not metrics.
 *
 * - Hook implementations and metric recorders should be lightweight and exception-safe.
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
    private val telemetryContextProvider: TelemetryContextProvider,
    private val listener: OperationListener,

    private val metricsContextFactory: MetricsContextFactory,
    private val metricOutcomeClassifier: MetricOutcomeClassifier,
    private val metricsRecorder: MetricsRecorder,
    private val metricsEnricher: MetricsEnricher,
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
        val telemetry = telemetryContextProvider.current()

        val baseCtx = OperationContext(
            correlationId = correlationIdProvider.newCorrelationId(),
            issuer = issuerProvider.currentIssuer(),
            entrypoint = info.entrypoint,
            service = info.service,
            function = info.function,
            operation = info.operation,
            useCase = info.useCase,
            event = info.event,
            attributes = info.attributes,
            telemetry = telemetry,
        )

        // Metrics scope starts here (backend-agnostic).
        val metrics = metricsContextFactory.create()
            .injectDescriptor(
                MetricDescriptor(
                    operation = baseCtx.operation,
                    useCase = baseCtx.useCase,
                    event = baseCtx.event,
                )
            ).start()

        val start = System.nanoTime()

        return try {
            val result = baseCtx.block()

            val durationMs = (System.nanoTime() - start) / 1_000_000

            val successCtx = baseCtx.copy(
                durationMs = durationMs,
                response = result.toString()
            )

            // Finalize metrics as SUCCESS (no HTTP status in core).
            val outcome = metricOutcomeClassifier.classify(
                statusCode = null,
                error = null
            )
            val finalized = metrics.end(outcome = outcome)
            val enriched = metricsEnricher.enrich(finalized)

            listener.onSuccess(successCtx)
            metricsRecorder.record(enriched)



            OperationResult(
                context = successCtx,
                metrics = enriched,
                data = result
            )
        } catch (exception: Throwable) {
            val durationMs = (System.nanoTime() - start) / 1_000_000

            val failureCtx = baseCtx.copy(
                durationMs = durationMs,
                response = exception::class.simpleName ?: "Exception"
            )

            // Finalize metrics as FAILURE/REJECT (based on classifier policy).
            val outcome = metricOutcomeClassifier.classify(
                statusCode = null,
                error = exception
            )
            val finalized = metrics.end(outcome = outcome)
            val enriched = metricsEnricher.enrich(finalized)

            listener.onFailure(failureCtx, exception)
            metricsRecorder.record(enriched)

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