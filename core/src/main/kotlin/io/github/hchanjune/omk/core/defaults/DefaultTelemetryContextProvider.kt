package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.models.context.TelemetryContext
import io.github.hchanjune.omk.core.providers.telemetry.TelemetryContextProvider

object DefaultTelemetryContextProvider: TelemetryContextProvider {
    override fun current(): TelemetryContext =
        TelemetryContext(
            traceId = "",
            causationId = "",
            spanId = "",
            baggage = emptyMap(),
        )
}