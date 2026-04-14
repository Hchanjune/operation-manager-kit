package io.github.hchanjune.omk.core.provider

interface TelemetryPropagationProvider {
    fun extractTraceId(headers: (String) -> String?): String?
    fun extractParentId(headers: (String) -> String?): String?
    fun inject(traceId: String, spanId: String, setter: (String, String) -> Unit)
}