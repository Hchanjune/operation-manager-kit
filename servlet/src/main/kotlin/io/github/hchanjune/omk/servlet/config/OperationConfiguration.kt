package io.github.hchanjune.omk.servlet.config

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.servlet.Operations
import io.github.hchanjune.omk.servlet.config.properties.OperationManagerServletConfigProperties
import io.github.hchanjune.omk.servlet.config.properties.TelemetryConfigureProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OperationManagerServletConfigProperties::class)
internal class OperationConfiguration {

    @Bean
    fun operationExecutor(): OperationExecutor =
        OperationExecutor()

    /**
     * This Spring context's own configuration bundle. Entry-point beans (event/schedule
     * aspects) receive it and attach it to every ManagedContext they open, so reads resolve
     * per-context instead of through the static default.
     */
    @Bean
    fun operationRuntime(
        executor: OperationExecutor,
        compositeHook: OperationHook,
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        properties: OperationManagerServletConfigProperties,
        telemetryProperties: TelemetryConfigureProperties
    ): OperationRuntime = OperationRuntime().apply {
        this.executor = executor
        this.hook = compositeHook
        this.defaultAsyncHookEnabled = properties.asyncPropagation.hookEnabled
        this.contextProvider = contextProvider
        this.traceIdProvider = traceIdProvider
        this.causationIdProvider = causationIdProvider
        this.generateWhenMissing = telemetryProperties.propagation.generateWhenMissing
    }

    @Bean
    fun operationInitializer(operationRuntime: OperationRuntime): Any {
        // Last context to start wins the static default — only reached by code running
        // outside any managed scope (detached fallback); managed executions resolve
        // through the runtime attached to their context.
        Operations.configureDefaultRuntime(operationRuntime)
        return Any()
    }

}
