package io.github.hchanjune.operationresult.core.providers

import io.github.hchanjune.operationresult.core.models.TelemetryContext

fun interface TelemetryContextProvider {
    fun current(): TelemetryContext
}