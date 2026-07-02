package io.github.hchanjune.omk.reactive.config

import io.github.hchanjune.omk.reactive.filter.ReactiveSpringSecurityWebFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

@Configuration
@ConditionalOnClass(name = [
    "org.springframework.security.core.context.ReactiveSecurityContextHolder",
    "org.springframework.security.web.server.SecurityWebFilterChain"
])
internal class SecurityFilterConfiguration {

    @Bean
    @Order(-80)
    fun reactiveSpringSecurityWebFilter(): ReactiveSpringSecurityWebFilter =
        ReactiveSpringSecurityWebFilter()
}
