package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.TelemetryContext
import io.github.hchanjune.operationresult.core.providers.TelemetryContextProvider

object DefaultTelemetryContextProvider: TelemetryContextProvider {
    override fun current(): TelemetryContext =
        TelemetryContext(
            traceId = "",
            spanId = ""
        )
}