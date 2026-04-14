package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.filter.ManagedContextPersistenceFilter
import io.github.hchanjune.omk.webmvc.filter.SpringSecurityConfigurationFilter
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.access.intercept.AuthorizationFilter
import kotlin.jvm.java

@Configuration
class FilterConfiguration {

    @Bean
    fun managedContextPersistenceFilter(
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        metricsRecorder: MetricsRecorder,
        compositeHook: OperationHook,
    ): FilterRegistrationBean<ManagedContextPersistenceFilter> =
        FilterRegistrationBean(
            ManagedContextPersistenceFilter(
                traceIdProvider = traceIdProvider,
                causationIdProvider = causationIdProvider,
                contextProvider = contextProvider,
                metricsRecorder = metricsRecorder,
                compositeHook = compositeHook
            )
        ).apply {
            setName("managedContextPersistenceFilter")
            addUrlPatterns("/*")
            order = -90
    }



}