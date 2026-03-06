package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.context.TelemetryContext
import io.github.hchanjune.operationresult.core.providers.telemetry.TelemetryContextProvider

object DefaultTelemetryContextProvider: TelemetryContextProvider {
    override fun current(): TelemetryContext =
        TelemetryContext(
            traceId = "",
            causationId = "",
            spanId = "",
            baggage = emptyMap(),
        )
}