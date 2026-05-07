package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.webmvc.aspect.ManagedAnnotationAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedControllerAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedRepositoryAspect
import io.github.hchanjune.omk.webmvc.aspect.ManagedServiceAspect
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
     * ###### ManagedControllerAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedControllerAspect(): ManagedControllerAspect =
        ManagedControllerAspect()

    /**
     * ###### ManagedServiceAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedServiceAspect(): ManagedServiceAspect =
        ManagedServiceAspect()

    /**
     * ###### ManagedAnnotationAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedAnnotationAspect(): ManagedAnnotationAspect =
        ManagedAnnotationAspect()

    /**
     * ###### ManagedRepositoryAspect
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.context-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedRepositoryAspect(): ManagedRepositoryAspect =
        ManagedRepositoryAspect()

}