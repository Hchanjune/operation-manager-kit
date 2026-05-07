package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
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
        metricsRecorder: MetricsRecorder,
        compositeHook: OperationHook,
    ): FilterRegistrationBean<ManagedContextPersistenceFilter> =
        FilterRegistrationBean(
            ManagedContextPersistenceFilter(
                contextProvider = contextProvider,
                propagationProvider = propagationProvider,
                traceIdProvider = traceIdProvider,
                causationIdProvider = causationIdProvider,
                metricsRecorder = metricsRecorder,
                compositeHook = compositeHook
            )
        ).apply {
            setName("managedContextPersistenceFilter")
            addUrlPatterns("/*")
            order = -90
    }



}