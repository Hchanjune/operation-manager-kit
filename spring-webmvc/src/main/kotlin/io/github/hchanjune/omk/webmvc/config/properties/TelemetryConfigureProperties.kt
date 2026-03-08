package io.github.hchanjune.omk.webmvc.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.webmvc.telemetry")
data class TelemetryConfigureProperties(
    var enabled: Boolean = true,
    var includeBaggage: Boolean = false,
    var baggageAllowList: Set<String> = emptySet(),
    var traceparentHeader: String,
    var causationHeader: String,
)