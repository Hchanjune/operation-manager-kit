package io.github.hchanjune.operationresult.webmvc.defaultListeners

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.webmvc.logging")
data class DefaultOperationLoggingProperties(
    var enabled: Boolean = true,
    var pretty: Boolean = false,
    var successLevel: LogLevel = LogLevel.INFO,
    var failureLevel: LogLevel = LogLevel.ERROR,
)
