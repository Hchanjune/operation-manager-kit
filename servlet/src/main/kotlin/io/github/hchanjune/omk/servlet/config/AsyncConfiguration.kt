package io.github.hchanjune.omk.servlet.config

import io.github.hchanjune.omk.servlet.async.ManagedContextTaskDecorator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.task.SimpleAsyncTaskExecutorCustomizer
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "operation-manager.servlet.async-propagation",
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

    // Covers spring.threads.virtual.enabled=true: Spring Boot switches @Async executor
    // to SimpleAsyncTaskExecutor backed by virtual threads; ThreadPoolTaskExecutorCustomizer
    // is not applied to it, so we register a separate customizer for this case.
    @Bean
    fun managedContextSimpleAsyncExecutorCustomizer(
        decorator: ManagedContextTaskDecorator
    ): SimpleAsyncTaskExecutorCustomizer =
        SimpleAsyncTaskExecutorCustomizer { executor -> executor.setTaskDecorator(decorator) }

}
