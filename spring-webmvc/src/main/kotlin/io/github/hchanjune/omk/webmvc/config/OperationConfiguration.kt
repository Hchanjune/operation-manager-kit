package io.github.hchanjune.omk.webmvc.config

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationsEntryPoint
import io.github.hchanjune.omk.core.defaults.CompositeOperationListener
import io.github.hchanjune.omk.core.defaults.DefaultOperationContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.providers.invocation.InvocationInfoProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.providers.metric.MetricOutcomeClassifier
import io.github.hchanjune.omk.core.providers.metric.MetricsContextProvider
import io.github.hchanjune.omk.core.providers.metric.MetricsEnricher
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.providers.operation.OmkContextHolderProvider
import io.github.hchanjune.omk.core.providers.operation.OperationContextProvider
import io.github.hchanjune.omk.core.OperationListener
import io.github.hchanjune.omk.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.omk.webmvc.filter.OmkFilter
import io.github.hchanjune.omk.webmvc.context.ThreadLocalManagedContextHolder
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotationAwareOrderComparator

@Configuration
internal class OperationConfiguration {

    /**
     * ###### OperationContextProvider
     */
    @Bean
    fun operationContextProvider(): OperationContextProvider =
        DefaultOperationContextProvider

    /**
     * ###### OperationContextHolderProvider
     */
    @Bean
    fun operationContextHolderProvider(): OmkContextHolderProvider {
        val holder = ThreadLocalManagedContextHolder()
        return OmkContextHolderProvider { holder }
    }

    /**
     * ###### Operation Listener
     */
    @Bean(name = ["operationCompositeListener"])
    @Primary
    @ConditionalOnMissingBean(/* ...value = */ CompositeOperationListener::class)
    fun operationCompositeListener(
        provider: ObjectProvider<List<OperationListener>>
    ): OperationListener {

        val listeners = (provider.ifAvailable ?: emptyList())
            .filterNot { it is CompositeOperationListener }

        val ordered = listeners.toMutableList().apply {
            AnnotationAwareOrderComparator.sort(this)
        }

        return CompositeOperationListener(ordered)
    }

    /**
     * ###### OperationExecutor
     */
    @Bean
    fun operationExecutor(
        contextHolderProvider: OmkContextHolderProvider,

        issuerProvider: IssuerProvider,
        invocationInfoProvider: InvocationInfoProvider,

        operationContextProvider: OperationContextProvider,
        metricsContextProvider: MetricsContextProvider,
        telemetryContextProvider: TelemetryContextProvider,

        spanIdProvider: SpanIdProvider,
        @Qualifier("operationCompositeListener") listener: OperationListener,

        metricOutcomeClassifier: MetricOutcomeClassifier,
        metricsEnricher: MetricsEnricher,
        metricsRecorder: MetricsRecorder,
    ): OperationExecutor =
        OperationExecutor(
            contextHolderProvider = contextHolderProvider,

            issuerProvider = issuerProvider,
            invocationInfoProvider = invocationInfoProvider,

            operationContextProvider = operationContextProvider,
            metricsContextProvider = metricsContextProvider,
            telemetryContextProvider = telemetryContextProvider,

            spanIdProvider = spanIdProvider,
            listener = listener,

            metricOutcomeClassifier = metricOutcomeClassifier,
            metricsRecorder = metricsRecorder,
            metricsEnricher = metricsEnricher
        )

    /**
     * ###### OperationExecutor Initializer
     */
    @Bean
    fun operationInitializer(executor: OperationExecutor): Any {
        OperationsEntryPoint.configure(executor)
        return Any()
    }

    /**
     * ###### Clear Filter
     * clears ThreadLocalContext and MDC
     */
    @Bean
    fun operationContextClearFilter(
        holderProvider: OmkContextHolderProvider
    ): FilterRegistrationBean<OmkFilter> {
        return FilterRegistrationBean(OmkFilter(holderProvider)).apply {
            order = Ordered.LOWEST_PRECEDENCE
            addUrlPatterns("/*")
            setName("operationContextClearFilter")
        }
    }
}