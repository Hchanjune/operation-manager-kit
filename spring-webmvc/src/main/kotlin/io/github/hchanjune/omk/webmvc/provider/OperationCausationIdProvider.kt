package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import java.security.SecureRandom
import java.util.UUID

class OperationCausationIdProvider(
    private val mode : TelemetryConfigureProperties.PropagationMode
): CausationIdProvider {

    private val random = SecureRandom()

    override fun provideCausationId(): String {
        return if (mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD) {
            val bytes = ByteArray(8)
            random.nextBytes(bytes)
             bytes.joinToString("") { "%02x".format(it) }
        } else {
            UUID.randomUUID().toString()
        }
    }

}