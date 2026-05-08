package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.webmvc.async.ManagedContextTaskDecorator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "operation-manager.webmvc.async-propagation",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
internal class AsyncConfiguration {

    @Bean
    fun managedContextTaskDecorator(): ManagedContextTaskDecorator =
        ManagedContextTaskDecorator()

    @Bean
    fun managedContextExecutorCustomizer(
        decorator: ManagedContextTaskDecorator
    ): ThreadPoolTaskExecutorCustomizer =
        ThreadPoolTaskExecutorCustomizer { executor -> executor.setTaskDecorator(decorator) }

}
