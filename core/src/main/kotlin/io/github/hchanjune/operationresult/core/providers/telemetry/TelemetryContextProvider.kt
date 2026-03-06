package io.github.hchanjune.operationresult.core.providers.telemetry

import io.github.hchanjune.operationresult.core.models.context.TelemetryContext

fun interface TelemetryContextProvider {
    fun current(): TelemetryContext
}