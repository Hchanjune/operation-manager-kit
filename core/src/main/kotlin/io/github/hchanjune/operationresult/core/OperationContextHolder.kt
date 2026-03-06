package io.github.hchanjune.operationresult.core

import io.github.hchanjune.operationresult.core.models.context.MetricsContext
import io.github.hchanjune.operationresult.core.models.context.OperationContext
import io.github.hchanjune.operationresult.core.models.context.TelemetryContext

interface OperationContextHolder {
    val operationContext: OperationContext?
    val metricContext: MetricsContext?
    val telemetryContext: TelemetryContext?

    fun applyOperationContext(operationContext: OperationContext)
    fun applyMetricContext(metricContext: MetricsContext)
    fun applyTelemetryContext(telemetryContext: TelemetryContext)

    fun clear()
}