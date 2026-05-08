package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.Operations
import io.github.hchanjune.omk.webmvc.config.properties.OperationManagerWebmvcAutoConfigProperties
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OperationManagerWebmvcAutoConfigProperties::class)
internal class OperationConfiguration {

    @Bean
    fun operationExecutor(): OperationExecutor =
        OperationExecutor()

    @Bean
    fun operationInitializer(
        executor: OperationExecutor,
        compositeHook: OperationHook,
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        properties: OperationManagerWebmvcAutoConfigProperties,
        telemetryProperties: TelemetryConfigureProperties
    ): Any {
        Operations.configure(executor)
        Operations.configureHook(compositeHook)
        Operations.configureDefaultAsyncHookEnabled(properties.asyncPropagation.hookEnabled)
        Operations.configureEventProviders(
            contextProvider = contextProvider,
            traceIdProvider = traceIdProvider,
            causationIdProvider = causationIdProvider,
            generateWhenMissing = telemetryProperties.propagation.generateWhenMissing
        )
        return Any()
    }

}
