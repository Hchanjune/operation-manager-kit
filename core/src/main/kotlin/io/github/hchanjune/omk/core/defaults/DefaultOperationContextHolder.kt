package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.OperationContextHolder
import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.models.context.OperationContext
import io.github.hchanjune.omk.core.models.context.TelemetryContext

class DefaultOperationContextHolder: OperationContextHolder {

    private var _operationContext: OperationContext? = null
    private var _metricContext: MetricsContext? = null
    private var _telemetryContext: TelemetryContext? = null

    override val operationContext: OperationContext?
        get() = _operationContext
    override val metricContext: MetricsContext?
        get() = _metricContext
    override val telemetryContext: TelemetryContext?
        get() = _telemetryContext

    override fun applyOperationContext(operationContext: OperationContext) {
        _operationContext = operationContext
    }

    override fun applyMetricContext(metricContext: MetricsContext) {
        _metricContext = metricContext
    }

    override fun applyTelemetryContext(telemetryContext: TelemetryContext) {
        _telemetryContext = telemetryContext
    }

    override fun clear() {
        _operationContext = null
        _metricContext = null
        _telemetryContext = null
    }

}