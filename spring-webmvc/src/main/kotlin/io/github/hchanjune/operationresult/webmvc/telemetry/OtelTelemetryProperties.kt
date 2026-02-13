package io.github.hchanjune.operationresult.webmvc.telemetry

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.webmvc.telemetry")
data class OtelTelemetryProperties(
    var enabled: Boolean = true,
    var includeBaggage: Boolean = false,
    var baggageAllowList: Set<String> = emptySet()
)
