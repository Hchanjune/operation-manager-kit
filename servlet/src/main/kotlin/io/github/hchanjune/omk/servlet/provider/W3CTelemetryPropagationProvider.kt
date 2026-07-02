package io.github.hchanjune.omk.servlet.provider

import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider

class W3CTelemetryPropagationProvider: TelemetryPropagationProvider {

    // W3C Trace Context spec: https://www.w3.org/TR/trace-context/
    // version(2hex)-traceId(32hex)-parentId(16hex)-flags(2hex)
    private fun parse(traceparent: String): List<String>? {
        val parts = traceparent.split("-")
        if (parts.size != 4) return null
        val (version, traceId, parentId, flags) = parts
        if (version.length != 2 || !version.isLowerHex() || version == "ff") return null
        if (traceId.length != 32 || !traceId.isLowerHex() || traceId.isAllZeros()) return null
        if (parentId.length != 16 || !parentId.isLowerHex() || parentId.isAllZeros()) return null
        if (flags.length != 2 || !flags.isLowerHex()) return null
        return parts
    }

    private fun String.isLowerHex(): Boolean = all { it in '0'..'9' || it in 'a'..'f' }
    private fun String.isAllZeros(): Boolean = all { it == '0' }

    override fun extractTraceId(headers: (String) -> String?): String? {
        val traceparent = headers("traceparent") ?: return null
        return parse(traceparent)?.get(1)
    }

    override fun extractParentId(headers: (String) -> String?): String? {
        val traceparent = headers("traceparent") ?: return null
        return parse(traceparent)?.get(2)
    }

    override fun inject(
        traceId: String,
        spanId: String,
        setter: (String, String) -> Unit
    ) {
        setter("traceparent", "00-$traceId-$spanId-01")
    }
}