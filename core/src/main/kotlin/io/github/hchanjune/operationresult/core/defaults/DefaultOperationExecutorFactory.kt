package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.OperationExecutor

object DefaultOperationExecutorFactory {
    fun create(): OperationExecutor =
        OperationExecutor(
            contextHolderProvider = DefaultOperationContextHolderProvider,

            issuerProvider = DefaultIssuerProvider,
            invocationInfoProvider = DefaultInvocationInfoProvider,
            operationContextProvider = DefaultOperationContextProvider,
            metricsContextProvider = DefaultMetricsContextProvider,
            telemetryContextProvider = DefaultTelemetryContextProvider,

            correlationIdProvider = DefaultCorrelationIdProvider,
            listener = CompositeOperationListener(emptyList()),

            metricOutcomeClassifier = DefaultMetricOutcomeClassifier,
            metricsRecorder = NoopMetricsRecorder,
            metricsEnricher = DefaultMetricsEnricher,
        )
}