package io.github.hchanjune.omk.servlet.config

import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.aspect.ManagedEventHandlerAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedOperationAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedScheduleAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedMetricAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedRepositoryAspect
import io.github.hchanjune.omk.servlet.aspect.ManagedServiceAspect
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(Aspect::class)
internal class AspectConfiguration {

    /**
     * ###### ManagedEventHandlerAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedEventHandlerAspect(
        spanIdProvider: SpanIdProvider,
    ): ManagedEventHandlerAspect =
        ManagedEventHandlerAspect(spanIdProvider = spanIdProvider)

    /**
     * ###### ManagedScheduleAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedScheduleAspect(
        spanIdProvider: SpanIdProvider,
    ): ManagedScheduleAspect =
        ManagedScheduleAspect(spanIdProvider = spanIdProvider)

    /**
     * ###### ManagedControllerAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedControllerAspect(spanIdProvider: SpanIdProvider): ManagedControllerAspect =
        ManagedControllerAspect(spanIdProvider)

    /**
     * ###### ManagedServiceAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedServiceAspect(): ManagedServiceAspect =
        ManagedServiceAspect()

    /**
     * ###### ManagedOperationAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedOperationAspect(spanIdProvider: SpanIdProvider): ManagedOperationAspect =
        ManagedOperationAspect(spanIdProvider)

    /**
     * ###### ManagedMetricAspect
     */

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedMetricAspect(
        spanIdProvider: SpanIdProvider,
    ): ManagedMetricAspect =
        ManagedMetricAspect(spanIdProvider)

    /**
     * ###### ManagedRepositoryAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedRepositoryAspect(spanIdProvider: SpanIdProvider): ManagedRepositoryAspect =
        ManagedRepositoryAspect(spanIdProvider)

}