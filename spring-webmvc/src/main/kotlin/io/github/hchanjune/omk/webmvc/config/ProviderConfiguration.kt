package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import io.github.hchanjune.omk.webmvc.provider.CustomTelemetryPropagationProvider
import io.github.hchanjune.omk.webmvc.provider.OperationCausationIdProvider
import io.github.hchanjune.omk.webmvc.provider.OperationManagedContextProvider
import io.github.hchanjune.omk.webmvc.provider.OperationSpanIdProvider
import io.github.hchanjune.omk.webmvc.provider.OperationTraceIdProvider
import io.github.hchanjune.omk.webmvc.provider.SpringSecurityIssuerProvider
import io.github.hchanjune.omk.webmvc.provider.W3CTelemetryPropagationProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ProviderConfiguration(
    private val telemetryConfigurationProperties: TelemetryConfigureProperties
) {

    /**
     * ###### TraceIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdProvider::class)
    fun traceIdProvider(): TraceIdProvider =
        OperationTraceIdProvider(
            mode = telemetryConfigurationProperties.propagation.mode
        )

    /**
     * ###### CausationIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(CausationIdProvider::class)
    fun causationIdProvider(): CausationIdProvider =
        OperationCausationIdProvider(
            mode = telemetryConfigurationProperties.propagation.mode
        )

    /**
     * ###### SpanIdProvider
     */
    @Bean
    @ConditionalOnMissingBean(SpanIdProvider::class)
    fun spanIdProvider(): SpanIdProvider =
        OperationSpanIdProvider(
            mode = telemetryConfigurationProperties.propagation.mode
        )

    /**
     * ###### IssuerProvider (SpringSecurity Enabled)
     */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun securityIssuerProvider(): IssuerProvider =
        SpringSecurityIssuerProvider()

    /**
     * ###### IssuerProvider (Fallback)
     */
    @Bean
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun fallbackSecurityIssuerProvider(): IssuerProvider =
        IssuerProvider { "anonymous" }

    @Bean
    fun telemetryPropagationProvider(): TelemetryPropagationProvider {
        return if (telemetryConfigurationProperties.propagation.mode == TelemetryConfigureProperties.PropagationMode.W3C_STANDARD) {
            W3CTelemetryPropagationProvider()
        } else {
            CustomTelemetryPropagationProvider(
                traceIdHeader = telemetryConfigurationProperties.propagation.customHeaders.traceId,
                causationIdHeader = telemetryConfigurationProperties.propagation.customHeaders.causationId,
            )
        }
    }


    /**
     * ###### ManagedContextProvider
     */
    @Bean
    fun managedContextProvider(
        spanIdProvider: SpanIdProvider,
    ): ManagedContextProvider =
        OperationManagedContextProvider(
            spanIdProvider = spanIdProvider,
        )

}