package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider

class W3CTelemetryPropagationProvider: TelemetryPropagationProvider {
    override fun extractTraceId(headers: (String) -> String?): String? {
        val traceparent = headers("traceparent")?: return null
        val parts = traceparent.split("-")
        return if (parts.size >= 4) parts[1] else null
    }

    override fun extractParentId(headers: (String) -> String?): String? {
        val traceparent = headers("traceparent") ?: return null
        val parts = traceparent.split("-")
        return if (parts.size >= 4) parts[2] else null
    }

    override fun inject(
        traceId: String,
        spanId: String,
        setter: (String, String) -> Unit
    ) {
        setter("traceparent", "00-$traceId-$spanId-01")
    }
}