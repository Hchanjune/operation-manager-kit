package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import java.util.UUID

class OperationSpanIdProvider(
    private val mode : TelemetryConfigureProperties.PropagationMode
): SpanIdProvider {
    override fun provideSpanId(): String {
        return UUID.randomUUID().toString()
    }
}