package io.github.hchanjune.operationresult.webmvc.autoconfig

import io.github.hchanjune.operationresult.core.providers.TelemetryContextProvider
import io.github.hchanjune.operationresult.webmvc.telemetry.OtelTelemetryProperties
import io.github.hchanjune.operationresult.webmvc.telemetry.OtelTelemetryContextProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@AutoConfigureBefore(OperationManagerWebMvcAutoConfiguration::class)
@EnableConfigurationProperties(OtelTelemetryProperties::class)
@ConditionalOnClass(
    name = [
        "org.springframework.web.servlet.DispatcherServlet",
        "io.opentelemetry.api.trace.Span"
    ]
)
class OperationOtelTelemetryWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.telemetry",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(TelemetryContextProvider::class)
    fun otelTelemetryContextProvider(
        props: OtelTelemetryProperties,
    ): TelemetryContextProvider =
        OtelTelemetryContextProvider(props)

}