package io.github.hchanjune.omk.reactive.config.auto

import io.github.hchanjune.omk.reactive.config.AspectConfiguration
import io.github.hchanjune.omk.reactive.config.FilterConfiguration
import io.github.hchanjune.omk.reactive.config.HooksConfiguration
import io.github.hchanjune.omk.reactive.config.MetricsConfiguration
import io.github.hchanjune.omk.reactive.config.OperationConfiguration
import io.github.hchanjune.omk.reactive.config.OtelHooksConfiguration
import io.github.hchanjune.omk.reactive.config.ProviderConfiguration
import io.github.hchanjune.omk.reactive.config.SecurityFilterConfiguration
import io.github.hchanjune.omk.reactive.config.properties.OperationManagerReactiveConfigProperties
import io.github.hchanjune.omk.reactive.config.properties.TelemetryConfigureProperties
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
@EnableConfigurationProperties(OperationManagerReactiveConfigProperties::class, TelemetryConfigureProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.reactive.DispatcherHandler"])
class OperationManagerReactiveAutoConfiguration
