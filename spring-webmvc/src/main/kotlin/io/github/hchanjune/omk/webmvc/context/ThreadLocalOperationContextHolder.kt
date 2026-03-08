package io.github.hchanjune.omk.webmvc.context

import io.github.hchanjune.omk.core.OperationContextHolder
import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.models.context.OperationContext
import io.github.hchanjune.omk.core.models.context.TelemetryContext

class ThreadLocalOperationContextHolder : OperationContextHolder {

    private val operationContextThreadLocal = ThreadLocal<OperationContext>()
    private val metricContextThreadLocal = ThreadLocal<MetricsContext>()
    private val telemetryContextThreadLocal = ThreadLocal<TelemetryContext>()

    override val operationContext: OperationContext?
        get() = operationContextThreadLocal.get()

    override val metricContext: MetricsContext?
        get() = metricContextThreadLocal.get()

    override val telemetryContext: TelemetryContext?
        get() = telemetryContextThreadLocal.get()

    override fun applyOperationContext(operationContext: OperationContext) {
        operationContextThreadLocal.set(operationContext)
    }

    override fun applyMetricContext(metricContext: MetricsContext) {
        metricContextThreadLocal.set(metricContext)
    }

    override fun applyTelemetryContext(telemetryContext: TelemetryContext) {
        telemetryContextThreadLocal.set(telemetryContext)
    }

    override fun clear() {
        operationContextThreadLocal.remove()
        metricContextThreadLocal.remove()
        telemetryContextThreadLocal.remove()
    }
}