package io.github.hchanjune.omk.servlet.config.properties

import io.github.hchanjune.omk.servlet.hooks.LogLevel
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.servlet.logging")
data class DefaultOperationLoggingProperties(
    var enabled: Boolean = true,
    var pretty: Boolean = false,
    var json: Boolean = true,
    var spans: Boolean = false,
    var response: Boolean = true,
    var successLevel: LogLevel = LogLevel.INFO,
    var failureLevel: LogLevel = LogLevel.ERROR,
    var clientErrorLevel: LogLevel = LogLevel.WARN,
)