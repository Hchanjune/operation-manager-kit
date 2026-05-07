package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties

class OperationTraceIdProvider(
    private val mode : TelemetryConfigureProperties.PropagationMode
): TraceIdProvider {

    override fun provideTraceId(): String =
        if (mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
            OperationIdGenerator.hex(16)
        else
            OperationIdGenerator.uuid()

}