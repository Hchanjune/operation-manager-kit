package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.defaults.DefaultTelemetryContextProvider
import io.github.hchanjune.omk.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.omk.webmvc.telemetry.OtelTelemetryContextProvider
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TelemetryConfigureProperties::class)
class TelemetryConfiguration {

    /**
     * ###### TelemetryContextProvider (OpenTelemetry)
     */
    @Bean
    @ConditionalOnClass(name = ["io.opentelemetry.api.trace.Span"])
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.telemetry",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(TelemetryContextProvider::class)
    fun otelTelemetryContextProvider(
        props: TelemetryConfigureProperties,
    ): TelemetryContextProvider = OtelTelemetryContextProvider(props)

    /**
     * ###### TelemetryContextProvider (Default)
     */
    @Bean
    @ConditionalOnMissingBean(TelemetryContextProvider::class)
    fun telemetryContextProvider(): TelemetryContextProvider =
        DefaultTelemetryContextProvider

}