package io.github.hchanjune.operationresult.webmvc.telemetry

import io.github.hchanjune.operationresult.core.models.TelemetryContext
import io.github.hchanjune.operationresult.core.providers.TelemetryContextProvider
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span

class OtelTelemetryContextProvider(
    private val props: OtelTelemetryProperties
): TelemetryContextProvider {

    override fun current(): TelemetryContext {
        val span = Span.current()
        val spanContext = span.spanContext

        if (!spanContext.isValid) {
            return TelemetryContext(
                traceId = "",
                spanId = "",
                baggage = emptyMap(),
            )
        }

        val baggage = if (props.includeBaggage) readBaggageAllowListed(props.baggageAllowList) else emptyMap()

        return TelemetryContext(
            traceId = spanContext.traceId,
            spanId = spanContext.spanId,
            baggage = baggage,
        )

    }

    private fun readBaggageAllowListed(allowList: Set<String>): Map<String, String> {
        val baggage = Baggage.current()
        if (baggage.isEmpty) return emptyMap()

        // If allowList is empty, include nothing by default
        if (allowList.isEmpty()) return emptyMap()

        val out = LinkedHashMap<String, String>(minOf(allowList.size, 16))
        for (k in allowList) {
            val entry = baggage.getEntryValue(k) ?: continue
            if (entry.isNotBlank()) out[k] = entry
        }
        return out
    }


}