package io.github.hchanjune.omk.webflux.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webflux.ReactiveOperations
import io.github.hchanjune.omk.webflux.config.properties.TelemetryConfigureProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class OperationConfiguration {

    @Bean
    fun reactiveOperationInitializer(
        compositeHook: OperationHook,
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        telemetryProperties: TelemetryConfigureProperties
    ): Any {
        ReactiveOperations.configureHook(compositeHook)
        ReactiveOperations.configureEventProviders(
            contextProvider = contextProvider,
            traceIdProvider = traceIdProvider,
            causationIdProvider = causationIdProvider,
            generateWhenMissing = telemetryProperties.propagation.generateWhenMissing
        )
        return Any()
    }
}
