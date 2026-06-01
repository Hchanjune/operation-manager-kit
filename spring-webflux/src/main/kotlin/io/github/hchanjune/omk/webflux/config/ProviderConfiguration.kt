package io.github.hchanjune.omk.webflux.config

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webflux.config.properties.TelemetryConfigureProperties
import io.github.hchanjune.omk.webflux.provider.CustomTelemetryPropagationProvider
import io.github.hchanjune.omk.webflux.provider.OperationCausationIdProvider
import io.github.hchanjune.omk.webflux.provider.OperationManagedContextProvider
import io.github.hchanjune.omk.webflux.provider.OperationSpanIdProvider
import io.github.hchanjune.omk.webflux.provider.OperationTraceIdProvider
import io.github.hchanjune.omk.webflux.provider.W3CTelemetryPropagationProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ProviderConfiguration(
    private val telemetryConfigureProperties: TelemetryConfigureProperties
) {

    @Bean
    @ConditionalOnMissingBean(TraceIdProvider::class)
    fun traceIdProvider(): TraceIdProvider =
        OperationTraceIdProvider(mode = telemetryConfigureProperties.propagation.mode)

    @Bean
    @ConditionalOnMissingBean(CausationIdProvider::class)
    fun causationIdProvider(): CausationIdProvider =
        OperationCausationIdProvider(mode = telemetryConfigureProperties.propagation.mode)

    @Bean
    @ConditionalOnMissingBean(SpanIdProvider::class)
    fun spanIdProvider(): SpanIdProvider =
        OperationSpanIdProvider(mode = telemetryConfigureProperties.propagation.mode)

    @Bean
    fun telemetryPropagationProvider(): TelemetryPropagationProvider =
        if (telemetryConfigureProperties.propagation.mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD)
            W3CTelemetryPropagationProvider()
        else
            CustomTelemetryPropagationProvider(
                traceIdHeader = telemetryConfigureProperties.propagation.customHeaders.traceId,
                causationIdHeader = telemetryConfigureProperties.propagation.customHeaders.causationId,
            )

    @Bean
    fun managedContextProvider(spanIdProvider: SpanIdProvider): ManagedContextProvider =
        OperationManagedContextProvider(spanIdProvider = spanIdProvider)
}
