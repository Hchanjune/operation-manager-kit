package io.github.hchanjune.operationresult.core.models

data class TelemetryContext(
    val traceId: String,
    val spanId: String,
    val baggage: Map<String, String> = emptyMap(),
)
