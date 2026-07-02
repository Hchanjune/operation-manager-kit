package io.github.hchanjune.omk.reactive.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.reactive.telemetry")
data class TelemetryConfigureProperties(
    var propagation: PropagationProperties = PropagationProperties(),
) {
    data class PropagationProperties(
        var mode: PropagationMode = PropagationMode.W3C_STANDARD,
        var customHeaders: CustomHeaders = CustomHeaders(),
        var generateWhenMissing: Boolean = true
    )

    data class CustomHeaders(
        var traceId: String = "X-Trace-Id",
        var causationId: String = "X-Causation-Id"
    )

    enum class PropagationMode {
        W3C_STANDARD,
        CUSTOM
    }
}
