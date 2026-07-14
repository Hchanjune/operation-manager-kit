package io.github.hchanjune.omk.reactive.config

import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.reactive.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedEventHandlerAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedMetricAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedOperationAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedRepositoryAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedScheduleAspect
import io.github.hchanjune.omk.reactive.aspect.ManagedServiceAspect
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnClass(Aspect::class)
internal class AspectConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedEventHandlerAspect(spanIdProvider: SpanIdProvider): ManagedEventHandlerAspect =
        ManagedEventHandlerAspect(spanIdProvider = spanIdProvider)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedScheduleAspect(spanIdProvider: SpanIdProvider): ManagedScheduleAspect =
        ManagedScheduleAspect(spanIdProvider = spanIdProvider)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedControllerAspect(spanIdProvider: SpanIdProvider): ManagedControllerAspect =
        ManagedControllerAspect(spanIdProvider)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedServiceAspect(): ManagedServiceAspect =
        ManagedServiceAspect()

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedOperationAspect(spanIdProvider: SpanIdProvider): ManagedOperationAspect =
        ManagedOperationAspect(spanIdProvider)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedMetricAspect(spanIdProvider: SpanIdProvider): ManagedMetricAspect =
        ManagedMetricAspect(spanIdProvider)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "operation-manager.reactive.context-aspect", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun managedRepositoryAspect(spanIdProvider: SpanIdProvider): ManagedRepositoryAspect =
        ManagedRepositoryAspect(spanIdProvider)
}
