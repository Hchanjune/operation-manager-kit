package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.defaults.DefaultTelemetryContextProvider
import io.github.hchanjune.omk.core.defaults.NoopMetricsRecorder

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