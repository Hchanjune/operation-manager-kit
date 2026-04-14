package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.webmvc.filter.SpringSecurityConfigurationFilter
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter

@Configuration
@ConditionalOnClass(name = [
    "org.springframework.security.core.context.SecurityContextHolder",
    "org.springframework.security.web.SecurityFilterChain"
])
class SecurityFilterConfiguration(
    private val issuerProvider: IssuerProvider
) {

    @Bean
    fun securityIssuerInjector(): BeanPostProcessor = object : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (bean is DefaultSecurityFilterChain) {
                val filters = bean.filters.toMutableList()
                val index = filters.indexOfFirst { it is AuthorizationFilter }
                if (index >= 0) {
                    filters.add(index, SpringSecurityConfigurationFilter(issuerProvider))
                    return DefaultSecurityFilterChain(bean.requestMatcher, filters)
                }
            }
            return bean
        }
    }

}