package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import java.security.SecureRandom
import java.util.UUID

class OperationTraceIdProvider(
    private val mode : TelemetryConfigureProperties.PropagationMode
): TraceIdProvider {

    private val random = SecureRandom()

    override fun provideTraceId(): String {
        return if (mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD) {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it) }
        } else {
            UUID.randomUUID().toString()
        }
    }
}