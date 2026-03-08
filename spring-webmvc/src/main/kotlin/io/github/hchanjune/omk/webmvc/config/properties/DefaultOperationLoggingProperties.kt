package io.github.hchanjune.omk.webmvc.config.properties

import io.github.hchanjune.omk.webmvc.defaultListeners.LogLevel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.webmvc.logging")
data class DefaultOperationLoggingProperties(
    var enabled: Boolean = true,
    var pretty: Boolean = false,
    var json: Boolean = true,
    var successLevel: LogLevel = LogLevel.INFO,
    var failureLevel: LogLevel = LogLevel.ERROR,
)