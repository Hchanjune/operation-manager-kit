package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties

class OperationCausationIdProvider(
    private val mode : TelemetryConfigureProperties.PropagationMode
): CausationIdProvider {

    override fun provideCausationId(): String =
        if (mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
            OperationIdGenerator.hex(8)
        else
            OperationIdGenerator.uuid()

}