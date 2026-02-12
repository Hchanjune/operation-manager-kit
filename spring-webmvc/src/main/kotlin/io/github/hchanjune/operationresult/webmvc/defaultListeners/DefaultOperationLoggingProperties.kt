package io.github.hchanjune.operationresult.webmvc.defaultListeners

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.logging")
data class DefaultOperationLoggingProperties(
    val enabled: Boolean = true,
    val pretty: Boolean = false,
    val successLevel: LogLevel = LogLevel.INFO,
    val failureLevel: LogLevel = LogLevel.ERROR,
)
