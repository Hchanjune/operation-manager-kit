package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.webmvc.metrics.ServletMetricsRecorder
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class MetricsConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry::class)
    @ConditionalOnBean(MeterRegistry::class)
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.micrometer",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun operationMetricRecorder(
        registry: MeterRegistry
    ): MetricsRecorder = ServletMetricsRecorder(registry)

    @Bean
    @ConditionalOnMissingBean(MetricsRecorder::class)
    fun noOpMetricsRecorder(): MetricsRecorder = MetricsRecorder { }

}