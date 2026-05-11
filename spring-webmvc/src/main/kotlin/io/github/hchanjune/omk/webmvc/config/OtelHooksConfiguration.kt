package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.webmvc.hooks.OtelOperationHook
import io.opentelemetry.api.trace.Tracer
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

@Configuration
@ConditionalOnClass(name = ["io.opentelemetry.api.trace.Tracer"])
internal class OtelHooksConfiguration {

    @Bean
    @ConditionalOnBean(type = ["io.opentelemetry.api.trace.Tracer"])
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.otel",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    @Order(70)
    fun otelOperationHook(tracer: Tracer): OperationHook = OtelOperationHook(tracer)
}
