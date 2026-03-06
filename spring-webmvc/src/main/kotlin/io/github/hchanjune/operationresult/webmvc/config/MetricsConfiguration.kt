package io.github.hchanjune.operationresult.webmvc.config

import io.github.hchanjune.operationresult.core.defaults.DefaultMetricsEnricher
import io.github.hchanjune.operationresult.core.defaults.NoopMetricsRecorder
import io.github.hchanjune.operationresult.core.models.metric.MetricName
import io.github.hchanjune.operationresult.core.providers.metric.MetricOutcomeClassifier
import io.github.hchanjune.operationresult.core.providers.metric.MetricsContextProvider
import io.github.hchanjune.operationresult.core.providers.metric.MetricsEnricher
import io.github.hchanjune.operationresult.core.providers.metric.MetricsRecorder
import io.github.hchanjune.operationresult.webmvc.metrics.MetricsFlushFilter
import io.github.hchanjune.operationresult.webmvc.metrics.OperationMetricsRecorder
import io.github.hchanjune.operationresult.webmvc.metrics.RoutingWebMvcMetricsRecorder
import io.github.hchanjune.operationresult.webmvc.metrics.WebMvcMetricOutcomeClassifier
import io.github.hchanjune.operationresult.webmvc.metrics.WebMvcMetricsContextProvider
import io.github.hchanjune.operationresult.webmvc.metrics.WebMvcMetricsEnricher
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered

@Configuration
internal class MetricsConfiguration {

    /**
     * ###### MetricContextProvider
     */
    @Bean
    fun metricsContextProvider(): MetricsContextProvider =
        WebMvcMetricsContextProvider(metricName = MetricName("operation.duration"))

    /**
     * ###### MetricOutcomeClassifier
     * Handles Failure Situation
     */
    @Bean
    fun metricOutcomeClassifier(): MetricOutcomeClassifier =
        WebMvcMetricOutcomeClassifier()

    /**
     * ###### MetricEnricher
     */
    @Bean
    @ConditionalOnClass(MeterRegistry::class)
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.micrometer",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(MetricsEnricher::class)
    fun webMvcMetricsEnricher(): MetricsEnricher =
        WebMvcMetricsEnricher()

    /**
     * ###### MetricEnricher (Fallback)
     */
    @Bean
    @ConditionalOnMissingBean(MetricsEnricher::class)
    fun fallbackMetricsEnricher(): MetricsEnricher =
        DefaultMetricsEnricher

    /**
     * ###### MetricsRecorder
     */
    @Bean(name = ["operationManagerOperationMetricRecorder"])
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.micrometer",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnClass(MeterRegistry::class)
    @ConditionalOnBean(MeterRegistry::class)
    fun operationMetricRecorder(
        @Suppress("SpringJavaInjectionPointsAutowiringInspection") registry: MeterRegistry
    ): MetricsRecorder =
        /**
         * The actual Micrometer-backed recorder implementation.
         *
         * This recorder writes operation-level metrics
         * (e.g., execution time, success/failure counters)
         * into the provided MeterRegistry.
         */
        OperationMetricsRecorder(registry)

    /**
     * ###### MetricsRecorder (Routing)
     * Rout
     */
    @Bean(name = ["operationManagerRoutingWebMvcMetricRecorder"])
    @Primary
    @ConditionalOnBean(name = ["operationManagerOperationMetricRecorder"])
    fun routingWebMvcMetricRecorder(
        @Qualifier("operationManagerOperationMetricRecorder") backend: MetricsRecorder
    ): MetricsRecorder =
        RoutingWebMvcMetricsRecorder(backend)

    /**
     * ###### MetricsRecorder (Fallback)
     */
    @Bean(name = ["operationMetricsFallbackRecorder"])
    @ConditionalOnMissingBean(MetricsRecorder::class)
    fun metricsRecorderFallback(): MetricsRecorder =
        NoopMetricsRecorder

    /**
     * ###### MetricsFilter
     */
    @Bean
    @ConditionalOnBean(name = ["operationManagerOperationMetricRecorder"])
    fun operationMetricsFlushFilter(
        @Qualifier("operationManagerOperationMetricRecorder") backend: MetricsRecorder,
        classifier: MetricOutcomeClassifier,
        enricher: MetricsEnricher,
    ): FilterRegistrationBean<MetricsFlushFilter> {
        val filter = MetricsFlushFilter(backend, classifier, enricher)
        return FilterRegistrationBean(filter).apply {
            order = Ordered.LOWEST_PRECEDENCE - 50
            addUrlPatterns("/*")
            setName("operationMetricsFlushFilter")
        }
    }

}