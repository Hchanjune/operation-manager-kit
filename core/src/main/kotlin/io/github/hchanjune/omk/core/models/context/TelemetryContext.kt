package io.github.hchanjune.omk.core.models.context

data class TelemetryContext(
    val traceId: String,
    val spanId: String,
    val causationId: String,
    val baggage: Map<String, String> = emptyMap(),
)