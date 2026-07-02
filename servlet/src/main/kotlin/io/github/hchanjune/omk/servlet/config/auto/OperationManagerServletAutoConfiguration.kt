package io.github.hchanjune.omk.servlet.config.auto

import io.github.hchanjune.omk.servlet.config.AspectConfiguration
import io.github.hchanjune.omk.servlet.config.AsyncConfiguration
import io.github.hchanjune.omk.servlet.config.ExceptionHandlingConfiguration
import io.github.hchanjune.omk.servlet.config.FilterConfiguration
import io.github.hchanjune.omk.servlet.config.HooksConfiguration
import io.github.hchanjune.omk.servlet.config.MetricsConfiguration
import io.github.hchanjune.omk.servlet.config.OperationConfiguration
import io.github.hchanjune.omk.servlet.config.OtelHooksConfiguration
import io.github.hchanjune.omk.servlet.config.ProviderConfiguration
import io.github.hchanjune.omk.servlet.config.SecurityFilterConfiguration
import io.github.hchanjune.omk.servlet.config.properties.OperationManagerServletConfigProperties
import io.github.hchanjune.omk.servlet.config.properties.TelemetryConfigureProperties
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
    AsyncConfiguration::class,
    ExceptionHandlingConfiguration::class,
)
@AutoConfigureAfter(
    name = [
        // Spring Boot 4.x (spring-boot-micrometer module)
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        // Spring Boot 3.x (spring-boot-actuator-autoconfigure module)
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
    ]
)
@EnableConfigurationProperties(OperationManagerServletConfigProperties::class, TelemetryConfigureProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
class OperationManagerServletAutoConfiguration