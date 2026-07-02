package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.OperationManagerWebmvcAutoConfigProperties
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import io.github.hchanjune.omk.webmvc.filter.ManagedContextPersistenceFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class FilterConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.context-filter",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedContextPersistenceFilter(
        contextProvider: ManagedContextProvider,
        propagationProvider: TelemetryPropagationProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        compositeHook: OperationHook,
        telemetryProperties: TelemetryConfigureProperties,
        properties: OperationManagerWebmvcAutoConfigProperties,
    ): ManagedContextPersistenceFilter = ManagedContextPersistenceFilter(
        contextProvider = contextProvider,
        propagationProvider = propagationProvider,
        traceIdProvider = traceIdProvider,
        causationIdProvider = causationIdProvider,
        compositeHook = compositeHook,
        generateWhenMissing = telemetryProperties.propagation.generateWhenMissing,
        excludeOptions = properties.contextFilter.excludeOptions
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.context-filter",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedContextPersistenceFilterRegistration(
        filter: ManagedContextPersistenceFilter
    ): FilterRegistrationBean<ManagedContextPersistenceFilter> =
        FilterRegistrationBean(filter).apply {
            setName("managedContextPersistenceFilter")
            addUrlPatterns("/*")
            order = -101 // must run before Spring Security (DEFAULT_FILTER_ORDER = -100)
        }

}