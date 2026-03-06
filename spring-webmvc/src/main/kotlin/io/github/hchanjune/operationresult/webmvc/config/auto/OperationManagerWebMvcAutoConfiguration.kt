package io.github.hchanjune.operationresult.webmvc.config.auto

import io.github.hchanjune.operationresult.webmvc.config.InvocationConfiguration
import io.github.hchanjune.operationresult.webmvc.config.MetricsConfiguration
import io.github.hchanjune.operationresult.webmvc.config.OperationConfiguration
import io.github.hchanjune.operationresult.webmvc.config.OperationListenerConfiguration
import io.github.hchanjune.operationresult.webmvc.config.TelemetryConfiguration
import io.github.hchanjune.operationresult.webmvc.config.properties.OperationManagerWebmvcAutoConfigProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import

@AutoConfiguration
@Import(
    InvocationConfiguration::class,
    OperationConfiguration::class,
    MetricsConfiguration::class,
    TelemetryConfiguration::class,
    OperationListenerConfiguration::class,
)
@AutoConfigureAfter(
    name = [
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
    ]
)
@EnableConfigurationProperties(OperationManagerWebmvcAutoConfigProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
class OperationManagerWebMvcAutoConfiguration