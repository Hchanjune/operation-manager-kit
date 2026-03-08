package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.models.context.OperationContext
import io.github.hchanjune.omk.core.models.context.TelemetryContext

interface OperationContextHolder {
    val operationContext: OperationContext?
    val metricContext: MetricsContext?
    val telemetryContext: TelemetryContext?

    fun applyOperationContext(operationContext: OperationContext)
    fun applyMetricContext(metricContext: MetricsContext)
    fun applyTelemetryContext(telemetryContext: TelemetryContext)

    fun clear()
}