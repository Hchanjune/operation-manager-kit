package io.github.hchanjune.operationresult.webmvc.config

import io.github.hchanjune.operationresult.core.OperationExecutor
import io.github.hchanjune.operationresult.core.Operations
import io.github.hchanjune.operationresult.core.defaults.CompositeOperationListener
import io.github.hchanjune.operationresult.core.defaults.DefaultOperationContextProvider
import io.github.hchanjune.operationresult.core.providers.invocation.CorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.invocation.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.invocation.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.metric.MetricOutcomeClassifier
import io.github.hchanjune.operationresult.core.providers.metric.MetricsContextProvider
import io.github.hchanjune.operationresult.core.providers.metric.MetricsEnricher
import io.github.hchanjune.operationresult.core.providers.metric.MetricsRecorder
import io.github.hchanjune.operationresult.core.providers.operation.OperationContextHolderProvider
import io.github.hchanjune.operationresult.core.providers.operation.OperationContextProvider
import io.github.hchanjune.operationresult.core.providers.operation.OperationListener
import io.github.hchanjune.operationresult.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.operationresult.webmvc.context.ThreadLocalContextClearFilter
import io.github.hchanjune.operationresult.webmvc.context.ThreadLocalOperationContextHolder
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
    fun operationContextHolderProvider():OperationContextHolderProvider {
        val holder = ThreadLocalOperationContextHolder()
        return OperationContextHolderProvider { holder }
    }

    /**
     * ###### Operation Listener
     */
    @Bean(name = ["operationCompositeListener"])
    @Primary
    @ConditionalOnMissingBean(CompositeOperationListener::class)
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
        contextHolderProvider: OperationContextHolderProvider,

        issuerProvider: IssuerProvider,
        invocationInfoProvider: InvocationInfoProvider,

        operationContextProvider: OperationContextProvider,
        metricsContextProvider: MetricsContextProvider,
        telemetryContextProvider: TelemetryContextProvider,

        correlationIdProvider: CorrelationIdProvider,
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

            correlationIdProvider = correlationIdProvider,
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
        Operations.configure(executor)
        return Any()
    }

    /**
     * ###### Clear Filter
     * clears ThreadLocalContext and MDC
     */
    @Bean
    fun operationContextClearFilter(
        holderProvider: OperationContextHolderProvider
    ): FilterRegistrationBean<ThreadLocalContextClearFilter> {
        return FilterRegistrationBean(ThreadLocalContextClearFilter(holderProvider)).apply {
            order = Ordered.LOWEST_PRECEDENCE
            addUrlPatterns("/*")
            setName("operationContextClearFilter")
        }
    }
}