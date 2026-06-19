package io.github.hchanjune.omk.webmvc.config.auto

import io.github.hchanjune.omk.webmvc.config.AspectConfiguration
import io.github.hchanjune.omk.webmvc.config.AsyncConfiguration
import io.github.hchanjune.omk.webmvc.config.ExceptionHandlingConfiguration
import io.github.hchanjune.omk.webmvc.config.FilterConfiguration
import io.github.hchanjune.omk.webmvc.config.HooksConfiguration
import io.github.hchanjune.omk.webmvc.config.MetricsConfiguration
import io.github.hchanjune.omk.webmvc.config.OperationConfiguration
import io.github.hchanjune.omk.webmvc.config.OtelHooksConfiguration
import io.github.hchanjune.omk.webmvc.config.ProviderConfiguration
import io.github.hchanjune.omk.webmvc.config.SecurityFilterConfiguration
import io.github.hchanjune.omk.webmvc.config.properties.OperationManagerWebmvcAutoConfigProperties
import io.github.hchanjune.omk.webmvc.config.properties.TelemetryConfigureProperties
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
@EnableConfigurationProperties(OperationManagerWebmvcAutoConfigProperties::class, TelemetryConfigureProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
class OperationManagerWebMvcAutoConfiguration