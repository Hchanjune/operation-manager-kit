package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider

class CustomTelemetryPropagationProvider(
    private val traceIdHeader: String,
    private val causationIdHeader: String,
): TelemetryPropagationProvider {
    override fun extractTraceId(headers: (String) -> String?): String? = headers(traceIdHeader)
    override fun extractParentId(headers: (String) -> String?): String? = headers(causationIdHeader)
    override fun inject(
        traceId: String,
        spanId: String,
        setter: (String, String) -> Unit
    ) {
        setter(traceIdHeader, traceId)
        setter(causationIdHeader, spanId)
    }
}