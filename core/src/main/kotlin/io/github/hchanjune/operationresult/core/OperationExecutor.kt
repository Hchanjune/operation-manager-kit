package io.github.hchanjune.operationresult.core

import io.github.hchanjune.operationresult.core.models.metric.MetricDescriptor
import io.github.hchanjune.operationresult.core.models.context.OperationContext
import io.github.hchanjune.operationresult.core.models.OperationResult
import io.github.hchanjune.operationresult.core.providers.invocation.CorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.invocation.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.invocation.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.metric.MetricOutcomeClassifier
import io.github.hchanjune.operationresult.core.providers.metric.MetricsContextProvider
import io.github.hchanjune.operationresult.core.providers.metric.MetricsEnricher
import io.github.hchanjune.operationresult.core.providers.metric.MetricsRecorder
import io.github.hchanjune.operationresult.core.providers.operation.OperationContextHolderProvider
import io.github.hchanjune.operationresult.core.providers.operation.OperationContextProvider
import io.github.hchanjune.operationresult.core.providers.operation.OperationListener
import io.github.hchanjune.operationresult.core.providers.telemetry.TelemetryContextProvider

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
 * 4. Create and start a backend-agnostic metrics scope via [MetricsContextProvider]
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
    private val contextHolderProvider: OperationContextHolderProvider,

    private val issuerProvider: IssuerProvider,
    private val invocationInfoProvider: InvocationInfoProvider,

    private val operationContextProvider: OperationContextProvider,
    private val metricsContextProvider: MetricsContextProvider,
    private val telemetryContextProvider: TelemetryContextProvider,

    private val correlationIdProvider: CorrelationIdProvider,
    private val listener: OperationListener,

    private val metricOutcomeClassifier: MetricOutcomeClassifier,
    private val metricsRecorder: MetricsRecorder,
    private val metricsEnricher: MetricsEnricher,
) {
    private val _contextHolder = contextHolderProvider.current()
    val context: OperationContextHolder
        get() = _contextHolder

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
        val issuer = issuerProvider.currentIssuer()
        val invocation = invocationInfoProvider.current()
        val operation = operationContextProvider.current(issuer, invocation)
        val metrics = metricsContextProvider.current()
        val telemetry = telemetryContextProvider.current()

        // Metrics scope starts here (backend-agnostic).
        metrics
            .injectDescriptor(
                MetricDescriptor(
                    operation = operation.operation,
                    useCase = operation.useCase,
                    event = operation.event,
                )
            ).start()

        // ContextHolder
        _contextHolder.applyOperationContext(operation)
        _contextHolder.applyMetricContext(metrics)
        _contextHolder.applyTelemetryContext(telemetry)

        val start = System.nanoTime()

        return try {
            val result = operation.block()

            val durationMs = (System.nanoTime() - start) / 1_000_000

            val successCtx = operation.copy(
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

            listener.onSuccess(successCtx, enriched, telemetry)
            metricsRecorder.record(enriched)


            OperationResult(
                operation = successCtx,
                metrics = enriched,
                telemetry = telemetry,
                data = result
            )
        } catch (exception: Throwable) {
            val durationMs = (System.nanoTime() - start) / 1_000_000

            val failureCtx = operation.copy(
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

            listener.onFailure(failureCtx, enriched, telemetry, exception)
            metricsRecorder.record(enriched)

            throw exception
        } finally {
            _contextHolder.clear()
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