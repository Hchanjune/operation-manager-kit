package io.github.hchanjune.operationresult.webmvc.config

import io.github.hchanjune.operationresult.core.providers.operation.OperationListener
import io.github.hchanjune.operationresult.webmvc.defaultListeners.DefaultOperationLoggingListener
import io.github.hchanjune.operationresult.webmvc.config.properties.DefaultOperationLoggingProperties
import io.github.hchanjune.operationresult.webmvc.defaultListeners.OperationLoggingListener
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

@Configuration
@EnableConfigurationProperties(DefaultOperationLoggingProperties::class)
internal class OperationListenerConfiguration {

    /**
     * ###### LoggingListener (Default)
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.logging",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(OperationLoggingListener::class)
    @Order(50)
    fun defaultLoggingListener(
        props: DefaultOperationLoggingProperties
    ): OperationListener =
        DefaultOperationLoggingListener(
            prettyLogger = LoggerFactory.getLogger("OperationManager.Pretty"),
            jsonLogger = LoggerFactory.getLogger("OperationManager.JSON"),
            props = props
        )

}
