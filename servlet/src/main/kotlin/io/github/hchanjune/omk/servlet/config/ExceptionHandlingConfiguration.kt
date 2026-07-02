package io.github.hchanjune.omk.servlet.config

import io.github.hchanjune.omk.servlet.exception.ExceptionCapturingResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerExceptionResolver

@Configuration
internal class ExceptionHandlingConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.exception-capture",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun exceptionCapturingResolver(): HandlerExceptionResolver = ExceptionCapturingResolver()

}
