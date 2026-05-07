package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties

class OperationSpanIdProvider(
    private val mode : TelemetryConfigureProperties.PropagationMode
): SpanIdProvider {

    override fun provideSpanId(): String =
        if (mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
            OperationIdGenerator.hex(8)
        else
            OperationIdGenerator.uuid()

}