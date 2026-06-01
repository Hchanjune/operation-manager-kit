package io.github.hchanjune.omk.webflux.config.auto

import io.github.hchanjune.omk.webflux.config.AspectConfiguration
import io.github.hchanjune.omk.webflux.config.FilterConfiguration
import io.github.hchanjune.omk.webflux.config.HooksConfiguration
import io.github.hchanjune.omk.webflux.config.MetricsConfiguration
import io.github.hchanjune.omk.webflux.config.OperationConfiguration
import io.github.hchanjune.omk.webflux.config.OtelHooksConfiguration
import io.github.hchanjune.omk.webflux.config.ProviderConfiguration
import io.github.hchanjune.omk.webflux.config.SecurityFilterConfiguration
import io.github.hchanjune.omk.webflux.config.properties.OperationManagerWebFluxAutoConfigProperties
import io.github.hchanjune.omk.webflux.config.properties.TelemetryConfigureProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(
    ProviderConfiguration::class,
    OperationConfiguration::class,
    MetricsConfiguration::class,
    HooksConfiguration::class,
    OtelHooksConfiguration::class,
    FilterConfiguration::class,
    SecurityFilterConfiguration::class,
    AspectConfiguration::class,
)
@AutoConfigureAfter(
    name = [
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
    ]
)
@EnableConfigurationProperties(OperationManagerWebFluxAutoConfigProperties::class, TelemetryConfigureProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.reactive.DispatcherHandler"])
class OperationManagerWebFluxAutoConfiguration
