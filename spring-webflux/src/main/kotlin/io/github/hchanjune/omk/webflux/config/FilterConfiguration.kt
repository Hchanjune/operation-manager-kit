package io.github.hchanjune.omk.webflux.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webflux.config.properties.TelemetryConfigureProperties
import io.github.hchanjune.omk.webflux.filter.ManagedContextWebFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

@Configuration
internal class FilterConfiguration {

    @Bean
    @Order(-90)
    @ConditionalOnProperty(
        prefix = "operation-manager.webflux.context-filter",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun managedContextWebFilter(
        contextProvider: ManagedContextProvider,
        propagationProvider: TelemetryPropagationProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        compositeHook: OperationHook,
        issuerProvider: IssuerProvider,
        telemetryProperties: TelemetryConfigureProperties,
    ): ManagedContextWebFilter = ManagedContextWebFilter(
        contextProvider = contextProvider,
        propagationProvider = propagationProvider,
        traceIdProvider = traceIdProvider,
        causationIdProvider = causationIdProvider,
        compositeHook = compositeHook,
        issuerProvider = issuerProvider,
        generateWhenMissing = telemetryProperties.propagation.generateWhenMissing
    )
}
