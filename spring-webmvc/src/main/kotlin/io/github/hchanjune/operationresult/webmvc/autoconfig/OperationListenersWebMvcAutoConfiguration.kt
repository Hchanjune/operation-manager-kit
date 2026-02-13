package io.github.hchanjune.operationresult.webmvc.autoconfig

import io.github.hchanjune.operationresult.core.providers.OperationListener
import io.github.hchanjune.operationresult.webmvc.defaultListeners.DefaultOperationLoggingListener
import io.github.hchanjune.operationresult.webmvc.defaultListeners.DefaultOperationLoggingProperties
import io.github.hchanjune.operationresult.webmvc.defaultListeners.OperationLoggingListener
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order

@AutoConfiguration
@AutoConfigureBefore(OperationManagerWebMvcAutoConfiguration::class)
@EnableConfigurationProperties(DefaultOperationLoggingProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
class OperationListenersWebMvcAutoConfiguration {

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
            prettyLogger = LoggerFactory.getLogger("OperationManager[Pretty]"),
            jsonLogger = LoggerFactory.getLogger("OperationManager[JSON]"),
            props = props
        )
}