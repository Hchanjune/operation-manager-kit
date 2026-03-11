package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.webmvc.config.properties.DefaultOperationLoggingProperties
import io.github.hchanjune.omk.webmvc.hooks.CompositeOperationHook
import io.github.hchanjune.omk.webmvc.hooks.DefaultOperationLoggingHook
import io.github.hchanjune.omk.webmvc.hooks.OperationLoggingHook
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.annotation.AnnotationAwareOrderComparator
import org.springframework.core.annotation.Order

@Configuration
@EnableConfigurationProperties(DefaultOperationLoggingProperties::class)
internal class HooksConfiguration {

    /**
     * ###### Hooks
     */
    @Bean
    @Primary
    fun operationCompositeHook(
        provider: ObjectProvider<List<OperationHook>>
    ): OperationHook {

        val listeners = (provider.ifAvailable ?: emptyList())
            .filterNot { it is CompositeOperationHook }

        val ordered = listeners.toMutableList().apply {
            AnnotationAwareOrderComparator.sort(this)
        }

        return CompositeOperationHook(ordered)
    }

    /**
     * ###### LoggingHook (Default)
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.logging",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(OperationLoggingHook::class)
    @Order(50)
    fun defaultLoggingListener(
        props: DefaultOperationLoggingProperties
    ): OperationHook =
        DefaultOperationLoggingHook(
            prettyLogger = LoggerFactory.getLogger("OperationManager.Pretty"),
            jsonLogger = LoggerFactory.getLogger("OperationManager.JSON"),
            props = props
        )

}