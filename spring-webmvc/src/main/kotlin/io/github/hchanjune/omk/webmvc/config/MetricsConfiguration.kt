package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.webmvc.metrics.ServletMetricsRecorder
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class MetricsConfiguration {

    /**
     * ###### MetricsRecorder
     */
    @Bean
    @ConditionalOnClass(MeterRegistry::class)
    @ConditionalOnBean(MeterRegistry::class)
    fun operationMetricRecorder(
        registry: MeterRegistry
    ): MetricsRecorder =
        /**
         * The actual Micrometer-backed recorder implementation.
         *
         * This recorder writes operation-level metrics
         * (e.g., execution time, success/failure counters)
         * into the provided MeterRegistry.
         */
        ServletMetricsRecorder(registry)

}