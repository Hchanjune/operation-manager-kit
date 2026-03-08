package io.github.hchanjune.omk.core.providers.telemetry

import io.github.hchanjune.omk.core.models.context.TelemetryContext

fun interface TelemetryContextProvider {
    fun current(): TelemetryContext
}