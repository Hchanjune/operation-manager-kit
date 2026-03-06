package io.github.hchanjune.operationresult.webmvc.telemetry

import io.github.hchanjune.operationresult.core.models.context.TelemetryContext
import io.github.hchanjune.operationresult.core.providers.telemetry.TelemetryContextProvider
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
                causationId = "",
                baggage = emptyMap(),
            )
        }

        val baggage = if (props.includeBaggage) readBaggageAllowListed(props.baggageAllowList) else emptyMap()

        return TelemetryContext(
            traceId = spanContext.traceId,
            spanId = spanContext.spanId,
            causationId = baggage["causation-id"]?: "",
            baggage = baggage,
        )

    }

    private fun readBaggageAllowListed(allowList: Set<String>): Map<String, String> {
        val baggage = Baggage.current()
        if (baggage.isEmpty) return emptyMap()

        // If allowList is empty, include nothing by default
        if (allowList.isEmpty()) return emptyMap()

        val out = LinkedHashMap<String, String>(minOf(allowList.size, 16))
        allowList.forEach { key ->
            baggage.getEntryValue(key)?.takeIf { it.isNotBlank() }?.let { value ->
                out[key] = value
            }
        }
        return out
    }


}