package io.github.hchanjune.operationresult.webmvc.autoconfig

import io.github.hchanjune.operationresult.core.Operations
import io.github.hchanjune.operationresult.core.OperationExecutor
import io.github.hchanjune.operationresult.core.defaults.DefaultCorrelationIdProvider
import io.github.hchanjune.operationresult.core.defaults.DefaultMetricsEnricher
import io.github.hchanjune.operationresult.core.defaults.NoopMetricsRecorder
import io.github.hchanjune.operationresult.core.defaults.CompositeOperationListener
import io.github.hchanjune.operationresult.core.defaults.DefaultTelemetryContextProvider
import io.github.hchanjune.operationresult.core.models.MetricName
import io.github.hchanjune.operationresult.core.providers.CorrelationIdProvider
import io.github.hchanjune.operationresult.core.providers.IssuerProvider
import io.github.hchanjune.operationresult.core.providers.InvocationInfoProvider
import io.github.hchanjune.operationresult.core.providers.MetricOutcomeClassifier
import io.github.hchanjune.operationresult.core.providers.MetricsContextFactory
import io.github.hchanjune.operationresult.core.providers.MetricsEnricher
import io.github.hchanjune.operationresult.core.providers.MetricsRecorder
import io.github.hchanjune.operationresult.core.providers.OperationListener
import io.github.hchanjune.operationresult.core.providers.TelemetryContextProvider
import io.github.hchanjune.operationresult.webmvc.aop.OperationServiceAspect
import io.github.hchanjune.operationresult.webmvc.interceptor.MdcEntrypointInterceptor
import io.github.hchanjune.operationresult.webmvc.invocation.MdcInvocationInfoProvider
import io.github.hchanjune.operationresult.webmvc.metrics.MetricsFlushFilter
import io.github.hchanjune.operationresult.webmvc.metrics.OperationMetricsRecorder
import io.github.hchanjune.operationresult.webmvc.metrics.RoutingWebMvcMetricsRecorder
import io.github.hchanjune.operationresult.webmvc.metrics.WebMvcMetricOutcomeClassifier
import io.github.hchanjune.operationresult.webmvc.metrics.WebMvcMetricsContextFactory
import io.github.hchanjune.operationresult.webmvc.metrics.WebMvcMetricsEnricher
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotationAwareOrderComparator
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring Boot auto-configuration for Spring MVC integration.
 *
 * ## Purpose
 * This auto-configuration integrates the core operation execution engine with
 * Spring MVC by providing:
 *
 * - MDC enrichment for controller entrypoints (via MVC interceptor)
 * - MDC enrichment for service/function (via Spring AOP aspect; opt-in by annotation)
 * - An MDC-backed [InvocationInfoProvider]
 * - Default hooks and issuer providers when missing
 * - An [OperationExecutor] wired with the resolved providers
 * - Global installation of the executor into [Operations]
 *
 * ## Activation
 * Enabled only when Spring MVC is present on the classpath
 * (`org.springframework.web.servlet.DispatcherServlet`).
 *
 * ## User overrides
 * The following beans can be overridden by the application:
 * - [InvocationInfoProvider]
 * - [OperationListener]
 * - [IssuerProvider]
 *
 * For MVC integration components:
 * - Provide your own [MdcEntrypointInterceptor] bean to replace the default interceptor
 * - Provide your own bean named `operationWebMvcConfigurer` to fully control interceptor registration
 * - Provide your own [OperationServiceAspect] bean to replace the default aspect
 *
 * ## Configuration properties
 * - `operationresult.webmvc.mdc-entrypoint-interceptor.enabled` (default: true)
 *   Enables registration of the MVC interceptor that writes `entrypoint` into MDC.
 *
 * - `operationresult.webmvc.mdc-service-aspect.enabled` (default: true)
 *   Enables registration of the Spring AOP aspect that writes `service` and `function` into MDC
 *   for `@OperationManaged`-annotated execution points.
 *
 * ## Notes
 * - The security-backed [IssuerProvider] is resolved via reflection to avoid a hard dependency
 *   on Spring Security classes.
 * - MDC is thread-local; async execution may require MDC propagation if consistent context is needed.
 */
@AutoConfiguration
@AutoConfigureAfter(
    name = [
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
    ]
)
@EnableConfigurationProperties(OperationManagerWebmvcProperties::class)
@ConditionalOnClass(name = ["org.springframework.web.servlet.DispatcherServlet"])
class OperationManagerWebMvcAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean(CorrelationIdProvider::class)
    fun correlationIdProvider(): CorrelationIdProvider =
        DefaultCorrelationIdProvider


    @Bean
    @ConditionalOnMissingBean(TelemetryContextProvider::class)
    fun telemetryContextProvider(): TelemetryContextProvider =
        DefaultTelemetryContextProvider

    /**
     * Default MVC interceptor that writes the resolved controller entrypoint into MDC.
     *
     * Controlled by:
     * - `operation-manager.webmvc.mdc-entrypoint-interceptor.enabled` (default: true)
     *
     * Applications can replace this by defining their own [MdcEntrypointInterceptor] bean.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-entrypoint-interceptor",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean
    fun operationEntryInterceptor(): MdcEntrypointInterceptor =
        MdcEntrypointInterceptor()

    /**
     * Registers the [MdcEntrypointInterceptor] into Spring MVC's interceptor chain.
     *
     * This bean is conditionally created only when the interceptor itself is enabled.
     *
     * Applications may take full control over interceptor registration by providing
     * a bean named `operationWebMvcConfigurer`.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-entrypoint-interceptor",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(name = ["operationWebMvcConfigurer"])
    fun operationWebMvcConfigurer(interceptor: MdcEntrypointInterceptor): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(interceptor)
            }
        }

    /**
     * Aspect that writes `service` and `function` into MDC for operation-managed execution points.
     *
     * Controlled by:
     * - `operation-manager.webmvc.mdc-service-aspect.enabled` (default: true)
     *
     * This bean is created only when Spring AOP infrastructure is present.
     * (Typically provided by adding `spring-boot-starter-aop`.)
     *
     * Applications can replace this by defining their own [OperationServiceAspect] bean.
     */
    @Bean
    @ConditionalOnClass(org.aspectj.lang.annotation.Aspect::class)
    @ConditionalOnProperty(
        prefix = "operation-manager.webmvc.mdc-service-aspect",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean
    fun operationServiceAspect(): OperationServiceAspect = OperationServiceAspect()


    /**
     * MDC-backed [InvocationInfoProvider].
     *
     * Reads keys such as `entrypoint`, `service`, and `function` from MDC and builds [InvocationInfo].
     *
     * Applications may override this provider by defining their own [InvocationInfoProvider] bean.
     */
    @Bean
    @ConditionalOnMissingBean(InvocationInfoProvider::class)
    fun invocationInfoProvider(): InvocationInfoProvider = MdcInvocationInfoProvider()

    /**
     * Default no-op [OperationListener] implementation.
     *
     * Applications may override hooks by defining a custom [OperationListener] bean.
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
     * [IssuerProvider] implementation backed by Spring Security authentication context.
     *
     * This bean is only created when Spring Security is available on the classpath.
     * Reflection is used to avoid direct class references and keep Spring Security as an optional dependency.
     *
     * Applications may override issuer resolution by defining their own [IssuerProvider] bean.
     */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.security.core.context.SecurityContextHolder"])
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun securityIssuerProvider(): IssuerProvider = IssuerProvider {
        try {
            val holder = Class.forName("org.springframework.security.core.context.SecurityContextHolder")
            val context = holder.getMethod("getContext").invoke(null)
            val auth = context.javaClass.getMethod("getAuthentication").invoke(context)
            val name = auth?.javaClass?.getMethod("getName")?.invoke(auth) as? String
            name ?: "anonymous"
        } catch (_: Throwable) {
            "anonymous"
        }
    }

    /**
     * Fallback [IssuerProvider] used when no other issuer provider is configured.
     *
     * Returns a constant `"anonymous"` issuer.
     */
    @Bean
    @ConditionalOnMissingBean(IssuerProvider::class)
    fun fallbackIssuerProvider(): IssuerProvider = IssuerProvider { "anonymous" }

    /**
     * Provides the default [MetricsContextFactory] for WebMVC environments.
     *
     * This factory creates a new [MetricsContext] for each operation scope and
     * enriches it with HTTP-related tags such as:
     * - request method
     * - URI template (route pattern)
     *
     * Applications may override this bean to customize metric names or tag policies.
     */
    @Bean
    fun metricsContextFactory(): MetricsContextFactory =
        WebMvcMetricsContextFactory(metricName = MetricName("operation.duration"))

    /**
     * Provides the default [MetricOutcomeClassifier] for WebMVC environments.
     *
     * This classifier determines whether an execution should be treated as:
     * - SUCCESS  (normal completion)
     * - REJECT   (client/validation/auth related errors)
     * - FAILURE  (server-side or unexpected errors)
     *
     * Applications may override this bean to apply custom classification rules.
     */
    @Bean
    fun metricOutcomeClassifier(): MetricOutcomeClassifier =
        WebMvcMetricOutcomeClassifier()

    /**
     * Registers the default WebMVC-specific [MetricsEnricher] when Micrometer is available.
     *
     * This enricher is only created if:
     * - Micrometer is present on the classpath
     * - No other [MetricsEnricher] bean has been provided by the application
     *
     * When enabled, it enriches operation metrics with low-cardinality HTTP tags such as:
     * - http.method
     * - http.route (URI template)
     * - result (success/reject/failure)
     *
     * This provides meaningful metrics out-of-the-box when exporting through Micrometer.
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
     * Registers the fallback core [DefaultMetricsEnricher].
     *
     * This enricher is used when:
     * - Micrometer is not present, or
     * - WebMVC-specific enrichment is disabled/unavailable, or
     * - The application has not provided a custom [MetricsEnricher]
     *
     * The default implementation attaches only minimal outcome tags
     * (e.g., result=success/failure) to keep core behavior lightweight.
     */
    @Bean
    @ConditionalOnMissingBean(MetricsEnricher::class)
    fun defaultMetricsEnricher(): MetricsEnricher =
        DefaultMetricsEnricher

    /**
     * Micrometer-based MetricsRecorder auto-configuration.
     *
     * This bean is only created when:
     *  - Micrometer is present on the classpath (MeterRegistry exists)
     *  - A MeterRegistry bean is actually registered in the application context
     *  - The feature is enabled via configuration property
     *
     * This allows the library to integrate with Spring Boot Actuator metrics
     * automatically, without forcing Micrometer as a hard dependency.
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
     * Primary routing MetricsRecorder.
     *
     * This bean wraps the Micrometer-backed recorder and becomes the primary
     * MetricsRecorder used by the framework.
     *
     * Routing can later be extended to support multiple backends or strategies
     * without changing the core recorder implementation.
     */
    @Bean(name = ["operationManagerRoutingWebMvcMetricRecorder"])
    @Primary
    @ConditionalOnBean(name = ["operationManagerOperationMetricRecorder"])
    fun routingWebMvcMetricRecorder(
        @Qualifier("operationManagerOperationMetricRecorder") backend: MetricsRecorder
    ): MetricsRecorder =
        RoutingWebMvcMetricsRecorder(backend)

    /**
     * Fallback MetricsRecorder.
     *
     * If no MetricsRecorder implementation is available (e.g., Micrometer is not
     * on the classpath or explicitly disabled), this No-Op recorder is registered.
     *
     * This guarantees that the framework can operate safely even without metrics,
     * avoiding missing-bean failures in consumer applications.
     */
    @Bean(name = ["operationMetricsFallbackRecorder"])
    @ConditionalOnMissingBean(MetricsRecorder::class)
    fun metricsRecorderFallback(): MetricsRecorder =
        NoopMetricsRecorder

    /**
     * Servlet filter responsible for flushing buffered metrics at the end of each request.
     *
     * This filter is only registered when the Micrometer recorder is active.
     * It ensures that operation metrics are properly finalized and emitted
     * after the HTTP request lifecycle completes.
     */
    @Bean
    @ConditionalOnBean(name = ["operationManagerOperationMetricRecorder"])
    fun operationMetricsFlushFilter(
        @Qualifier("operationManagerOperationMetricRecorder") backend: MetricsRecorder
    ): FilterRegistrationBean<MetricsFlushFilter> {
        val filter = MetricsFlushFilter(backend)
        return FilterRegistrationBean(filter).apply {
            order = Ordered.LOWEST_PRECEDENCE - 50
            addUrlPatterns("/*")
            setName("operationMetricsFlushFilter")
        }
    }

    /**
     * Creates the core [OperationExecutor] used by this application.
     *
     * ## Responsibilities
     * This executor acts as the primary execution boundary for operations and is wired with:
     *
     * ### Invocation metadata providers
     * - [InvocationInfoProvider] (entrypoint/service/function resolution)
     * - [IssuerProvider] (actor identity resolution)
     * - [DefaultCorrelationIdProvider] (trace identifier generation)
     *
     * ### Lifecycle hooks
     * - [OperationListener] for success/failure callbacks
     *
     * ### Metrics pipeline (aggregated monitoring)
     * - [MetricsContextFactory] to create and enrich a metrics scope
     * - [MetricOutcomeClassifier] to classify outcomes (success/reject/failure)
     * - [MetricsEnricher] to convert metric outcome to usable object
     * - [MetricsRecorder] to record finalized metrics into an external backend
     *
     * ## Notes
     * - The default recorder is [NoopMetricsRecorder], meaning metrics are disabled
     *   unless an integration module (e.g., Micrometer) provides a real implementation.
     * - WebMVC-specific factories/classifiers may enrich tags such as HTTP method and URI template.
     * - This configuration remains independent of Spring Security types.
     */
    @Bean
    fun operationExecutor(
        invocationInfoProvider: InvocationInfoProvider,
        issuerProvider: IssuerProvider,
        correlationIdProvider: CorrelationIdProvider,
        telemetryContextProvider: TelemetryContextProvider,
        @Qualifier("operationCompositeListener") listener: OperationListener,

        metricsContextFactory: MetricsContextFactory,
        metricOutcomeClassifier: MetricOutcomeClassifier,
        metricsEnricher: MetricsEnricher,
        metricsRecorder: MetricsRecorder,
    ): OperationExecutor =
        OperationExecutor(
            invocationInfoProvider = invocationInfoProvider,
            issuerProvider = issuerProvider,
            correlationIdProvider = correlationIdProvider,
            telemetryContextProvider = telemetryContextProvider,
            listener = listener,
            metricsContextFactory = metricsContextFactory,
            metricOutcomeClassifier = metricOutcomeClassifier,
            metricsRecorder = metricsRecorder,
            metricsEnricher = metricsEnricher
        )

    /**
     * Installs the configured [OperationExecutor] into the global [Operations] entrypoint.
     *
     * This bean exists solely to trigger initialization during application startup.
     */
    @Bean
    fun operationInitializer(executor: OperationExecutor): Any {
        Operations.configure(executor)
        return Any()
    }
}
