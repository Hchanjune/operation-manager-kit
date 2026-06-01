package io.github.hchanjune.omk.webflux.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.webflux.config.properties.DefaultOperationLoggingProperties
import io.github.hchanjune.omk.webflux.hooks.CompositeOperationHook
import io.github.hchanjune.omk.webflux.hooks.DefaultOperationLoggingHook
import io.github.hchanjune.omk.webflux.hooks.MetricsOperationHook
import io.github.hchanjune.omk.webflux.hooks.OperationLoggingHook
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

    @Bean
    @Primary
    fun operationCompositeHook(provider: ObjectProvider<OperationHook>): OperationHook {
        val ordered = provider.stream()
            .filter { it !is CompositeOperationHook }
            .toList()
            .toMutableList()
            .apply { AnnotationAwareOrderComparator.sort(this) }
        return CompositeOperationHook(ordered)
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webflux.logging",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(OperationLoggingHook::class)
    @Order(50)
    fun defaultLoggingHook(props: DefaultOperationLoggingProperties): OperationHook =
        DefaultOperationLoggingHook(
            prettyLogger = LoggerFactory.getLogger("OperationManager.Pretty"),
            jsonLogger = LoggerFactory.getLogger("OperationManager.JSON"),
            props = props
        )

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webflux.micrometer",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Order(60)
    fun metricsOperationHook(metricsRecorder: MetricsRecorder): OperationHook =
        MetricsOperationHook(metricsRecorder)
}
