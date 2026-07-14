package io.github.hchanjune.omk.servlet.config.properties

import io.github.hchanjune.omk.core.hooks.LogLevel
import io.github.hchanjune.omk.core.hooks.OperationLoggingSettings
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("operation-manager.servlet.logging")
data class DefaultOperationLoggingProperties(
    override var enabled: Boolean = true,
    override var pretty: Boolean = false,
    override var json: Boolean = true,
    override var spans: Boolean = false,
    override var response: Boolean = true,
    override var ip: Boolean = false,
    override var successLevel: LogLevel = LogLevel.INFO,
    override var failureLevel: LogLevel = LogLevel.ERROR,
    override var clientErrorLevel: LogLevel = LogLevel.WARN,
) : OperationLoggingSettings
