package io.github.hchanjune.omk.servlet.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.servlet.config.properties.OperationManagerServletConfigProperties
import io.github.hchanjune.omk.servlet.config.properties.TelemetryConfigureProperties
import io.github.hchanjune.omk.servlet.filter.ManagedContextPersistenceFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class FilterConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-filter",
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
        properties: OperationManagerServletConfigProperties,
        operationRuntime: OperationRuntime,
    ): ManagedContextPersistenceFilter = ManagedContextPersistenceFilter(
        contextProvider = contextProvider,
        propagationProvider = propagationProvider,
        traceIdProvider = traceIdProvider,
        causationIdProvider = causationIdProvider,
        compositeHook = compositeHook,
        generateWhenMissing = telemetryProperties.propagation.generateWhenMissing,
        excludeOptions = properties.contextFilter.excludeOptions,
        runtime = operationRuntime,
    )

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.context-filter",
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