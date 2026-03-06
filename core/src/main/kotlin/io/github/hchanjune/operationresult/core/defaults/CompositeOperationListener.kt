package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.context.MetricsContext
import io.github.hchanjune.operationresult.core.models.context.OperationContext
import io.github.hchanjune.operationresult.core.models.context.TelemetryContext
import io.github.hchanjune.operationresult.core.providers.operation.OperationListener

class CompositeOperationListener(
    private val delegates: List<OperationListener>
): OperationListener {

    override fun onSuccess(operation: OperationContext, metrics: MetricsContext, telemetry: TelemetryContext) {
        forEachSafely { it.onSuccess(operation, metrics, telemetry) }
    }

    override fun onFailure(operation: OperationContext, metrics: MetricsContext, telemetry: TelemetryContext, exception: Throwable) {
        forEachSafely { it.onFailure(operation, metrics, telemetry, exception) }
    }

    private inline fun forEachSafely(block: (OperationListener) -> Unit) {
        for (delegate in delegates) {
            runCatching { block(delegate) }
                .onFailure { it.printStackTrace() }
        }
    }

}