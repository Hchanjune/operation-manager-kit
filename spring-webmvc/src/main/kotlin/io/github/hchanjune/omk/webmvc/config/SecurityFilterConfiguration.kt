package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.webmvc.filter.SpringSecurityConfigurationFilter
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
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
        override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
            if (bean is HttpSecurity) {
                bean.addFilterAfter(
                    SpringSecurityConfigurationFilter(issuerProvider),
                    AuthorizationFilter::class.java
                )
            }
            return bean
        }
    }

}