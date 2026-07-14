package io.github.hchanjune.omk.reactive.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.ReactiveOperations
import io.github.hchanjune.omk.reactive.config.properties.TelemetryConfigureProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class OperationConfiguration {

    /**
     * This Spring context's own configuration bundle. Entry-point beans (web filter,
     * event/schedule aspects) receive it and attach it to every ManagedContext they open,
     * so reads resolve per-context instead of through the static default.
     */
    @Bean
    fun operationRuntime(
        compositeHook: OperationHook,
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        telemetryProperties: TelemetryConfigureProperties
    ): OperationRuntime = OperationRuntime().apply {
        this.hook = compositeHook
        this.contextProvider = contextProvider
        this.traceIdProvider = traceIdProvider
        this.causationIdProvider = causationIdProvider
        this.generateWhenMissing = telemetryProperties.propagation.generateWhenMissing
    }

    @Bean
    fun reactiveOperationInitializer(operationRuntime: OperationRuntime): Any {
        // Last context to start wins the static default — only reached by code running
        // outside any managed scope (detached fallback); managed executions resolve
        // through the runtime attached to their context.
        ReactiveOperations.configureDefaultRuntime(operationRuntime)
        return Any()
    }
}
