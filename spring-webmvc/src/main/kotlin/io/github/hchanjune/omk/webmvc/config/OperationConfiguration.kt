package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.webmvc.Operations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class OperationConfiguration {

    /**
     * ###### OperationExecutor
     */
    @Bean
    fun operationExecutor(): OperationExecutor =
        OperationExecutor()

    /**
     * ###### OperationExecutor Initializer
     */
    @Bean
    fun operationInitializer(executor: OperationExecutor): Any {
        Operations.configure(executor)
        return Any()
    }

}