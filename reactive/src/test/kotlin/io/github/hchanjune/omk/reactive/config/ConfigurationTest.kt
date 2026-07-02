package io.github.hchanjune.omk.reactive.config

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.reactive.config.auto.OperationManagerReactiveAutoConfiguration
import io.github.hchanjune.omk.reactive.config.properties.DefaultOperationLoggingProperties
import io.github.hchanjune.omk.reactive.config.properties.OperationManagerReactiveConfigProperties
import io.github.hchanjune.omk.reactive.config.properties.TelemetryConfigureProperties
import io.github.hchanjune.omk.reactive.hooks.LogLevel
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.opentelemetry.api.OpenTelemetry
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigurationTest {

    private val defaultTelemetryProps = TelemetryConfigureProperties()

    // ── config.properties ─────────────────────────────────────────────────────

    @Test
    fun `OperationManagerReactiveConfigProperties default values are all enabled`() {
        val props = OperationManagerReactiveConfigProperties()
        assertTrue(props.contextFilter.enabled)
        assertTrue(props.contextFilter.excludeOptions)
        assertTrue(props.contextAspect.enabled)
        assertTrue(props.micrometer.enabled)
    }

    @Test
    fun `OperationManagerReactiveConfigProperties Toggle can be disabled`() {
        val toggle = OperationManagerReactiveConfigProperties.Toggle(enabled = false)
        assertFalse(toggle.enabled)
        val props = OperationManagerReactiveConfigProperties(
            contextFilter = OperationManagerReactiveConfigProperties.ContextFilter(enabled = false),
            contextAspect = OperationManagerReactiveConfigProperties.Toggle(false),
            micrometer = OperationManagerReactiveConfigProperties.Toggle(false)
        )
        assertFalse(props.contextFilter.enabled)
        assertFalse(props.contextAspect.enabled)
        assertFalse(props.micrometer.enabled)
    }

    @Test
    fun `DefaultOperationLoggingProperties default values`() {
        val props = DefaultOperationLoggingProperties()
        assertTrue(props.enabled)
        assertFalse(props.pretty)
        assertTrue(props.json)
        assertFalse(props.spans)
        assertTrue(props.response)
        assertEquals(LogLevel.INFO, props.successLevel)
        assertEquals(LogLevel.ERROR, props.failureLevel)
    }

    @Test
    fun `DefaultOperationLoggingProperties accepts custom values`() {
        val props = DefaultOperationLoggingProperties(
            enabled = false,
            pretty = true,
            json = false,
            spans = true,
            response = false,
            successLevel = LogLevel.DEBUG,
            failureLevel = LogLevel.WARN
        )
        assertFalse(props.enabled)
        assertTrue(props.pretty)
        assertFalse(props.json)
        assertTrue(props.spans)
        assertFalse(props.response)
        assertEquals(LogLevel.DEBUG, props.successLevel)
        assertEquals(LogLevel.WARN, props.failureLevel)
    }

    @Test
    fun `TelemetryConfigureProperties default values`() {
        val props = TelemetryConfigureProperties()
        assertEquals(TelemetryConfigureProperties.PropagationMode.W3C_STANDARD, props.propagation.mode)
        assertEquals("X-Trace-Id", props.propagation.customHeaders.traceId)
        assertEquals("X-Causation-Id", props.propagation.customHeaders.causationId)
        assertTrue(props.propagation.generateWhenMissing)
    }

    @Test
    fun `TelemetryConfigureProperties CUSTOM mode with custom headers`() {
        val props = TelemetryConfigureProperties(
            propagation = TelemetryConfigureProperties.PropagationProperties(
                mode = TelemetryConfigureProperties.PropagationMode.CUSTOM,
                customHeaders = TelemetryConfigureProperties.CustomHeaders(
                    traceId = "X-My-Trace",
                    causationId = "X-My-Cause"
                ),
                generateWhenMissing = false
            )
        )
        assertEquals(TelemetryConfigureProperties.PropagationMode.CUSTOM, props.propagation.mode)
        assertEquals("X-My-Trace", props.propagation.customHeaders.traceId)
        assertEquals("X-My-Cause", props.propagation.customHeaders.causationId)
        assertFalse(props.propagation.generateWhenMissing)
    }

    // ── ProviderConfiguration ─────────────────────────────────────────────────

    @Test
    fun `ProviderConfiguration creates all providers in W3C mode`() {
        val config = ProviderConfiguration(defaultTelemetryProps)
        val spanId = config.spanIdProvider()
        assertNotNull(config.traceIdProvider().provideTraceId())
        assertNotNull(config.causationIdProvider().provideCausationId())
        assertNotNull(spanId.provideSpanId())
        assertNotNull(config.fallbackIssuerProvider())
        assertNotNull(config.telemetryPropagationProvider())
        assertNotNull(config.managedContextProvider(spanId).provide())
    }

    @Test
    fun `ProviderConfiguration creates CUSTOM propagation provider`() {
        val customProps = TelemetryConfigureProperties(
            propagation = TelemetryConfigureProperties.PropagationProperties(
                mode = TelemetryConfigureProperties.PropagationMode.CUSTOM
            )
        )
        val config = ProviderConfiguration(customProps)
        assertNotNull(config.telemetryPropagationProvider())
        assertNotNull(config.traceIdProvider())
        assertNotNull(config.causationIdProvider())
        assertNotNull(config.spanIdProvider())
    }

    // ── HooksConfiguration ────────────────────────────────────────────────────

    @Test
    fun `HooksConfiguration creates composite hook from empty provider`() {
        val hook = HooksConfiguration().operationCompositeHook(emptyObjectProvider())
        assertNotNull(hook)
    }

    @Test
    fun `HooksConfiguration creates composite hook from provider with hooks`() {
        val customHook = object : OperationHook {}
        val hook = HooksConfiguration().operationCompositeHook(singletonObjectProvider(customHook))
        assertNotNull(hook)
    }

    @Test
    fun `HooksConfiguration creates default logging hook`() {
        val hook = HooksConfiguration().defaultLoggingHook(DefaultOperationLoggingProperties())
        assertNotNull(hook)
    }

    @Test
    fun `HooksConfiguration creates metrics hook with no-op recorder`() {
        val recorder = MetricsConfiguration().noOpMetricsRecorder()
        val hook = HooksConfiguration().metricsOperationHook(recorder)
        assertNotNull(hook)
    }

    // ── MetricsConfiguration ──────────────────────────────────────────────────

    @Test
    fun `MetricsConfiguration creates no-op MetricsRecorder`() {
        assertNotNull(MetricsConfiguration().noOpMetricsRecorder())
    }

    @Test
    fun `MetricsConfiguration creates ReactiveMetricsRecorder with MeterRegistry`() {
        val recorder = MetricsConfiguration().operationMetricRecorder(SimpleMeterRegistry())
        assertNotNull(recorder)
    }

    // ── AspectConfiguration ───────────────────────────────────────────────────

    @Test
    fun `AspectConfiguration creates all aspect beans`() {
        val config = AspectConfiguration()
        val spanId = ProviderConfiguration(defaultTelemetryProps).spanIdProvider()
        assertNotNull(config.managedControllerAspect(spanId))
        assertNotNull(config.managedServiceAspect())
        assertNotNull(config.managedOperationAspect(spanId))
        assertNotNull(config.managedMetricAspect(spanId))
        assertNotNull(config.managedRepositoryAspect(spanId))
        assertNotNull(config.managedEventHandlerAspect(spanId))
    }

    // ── FilterConfiguration ───────────────────────────────────────────────────

    @Test
    fun `FilterConfiguration creates ManagedContextWebFilter`() {
        val provConfig = ProviderConfiguration(defaultTelemetryProps)
        val spanId = provConfig.spanIdProvider()
        val filter = FilterConfiguration().managedContextWebFilter(
            contextProvider = provConfig.managedContextProvider(spanId),
            propagationProvider = provConfig.telemetryPropagationProvider(),
            traceIdProvider = provConfig.traceIdProvider(),
            causationIdProvider = provConfig.causationIdProvider(),
            compositeHook = HooksConfiguration().operationCompositeHook(emptyObjectProvider()),
            issuerProvider = provConfig.fallbackIssuerProvider(),
            telemetryProperties = defaultTelemetryProps,
            properties = OperationManagerReactiveConfigProperties()
        )
        assertNotNull(filter)
    }

    // ── OperationConfiguration ────────────────────────────────────────────────

    @Test
    fun `OperationConfiguration initializes ReactiveOperations`() {
        val provConfig = ProviderConfiguration(defaultTelemetryProps)
        val spanId = provConfig.spanIdProvider()
        val result = OperationConfiguration().reactiveOperationInitializer(
            compositeHook = HooksConfiguration().operationCompositeHook(emptyObjectProvider()),
            contextProvider = provConfig.managedContextProvider(spanId),
            traceIdProvider = provConfig.traceIdProvider(),
            causationIdProvider = provConfig.causationIdProvider(),
            telemetryProperties = defaultTelemetryProps
        )
        assertNotNull(result)
    }

    // ── SecurityFilterConfiguration ───────────────────────────────────────────

    @Test
    fun `SecurityFilterConfiguration creates ReactiveSpringSecurityWebFilter`() {
        assertNotNull(SecurityFilterConfiguration().reactiveSpringSecurityWebFilter())
    }

    // ── OtelHooksConfiguration ────────────────────────────────────────────────

    @Test
    fun `OtelHooksConfiguration creates otel hook with noop tracer`() {
        val tracer = OpenTelemetry.noop().getTracer("test")
        val hook = OtelHooksConfiguration().otelOperationHook(tracer)
        assertNotNull(hook)
    }

    // ── OperationManagerReactiveAutoConfiguration ──────────────────────────────

    @Test
    fun `OperationManagerReactiveAutoConfiguration is instantiatable`() {
        assertNotNull(OperationManagerReactiveAutoConfiguration())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T : Any> emptyObjectProvider(): ObjectProvider<T> = object : ObjectProvider<T> {
        override fun getObject(): T = throw NoSuchElementException()
        override fun getObject(vararg args: Any?): T = throw NoSuchElementException()
        override fun getIfAvailable(): T? = null
        override fun getIfUnique(): T? = null
        override fun iterator(): MutableIterator<T> = mutableListOf<T>().iterator()
        override fun stream(): java.util.stream.Stream<T> = java.util.stream.Stream.empty()
    }

    private fun <T : Any> singletonObjectProvider(item: T): ObjectProvider<T> = object : ObjectProvider<T> {
        override fun getObject(): T = item
        override fun getObject(vararg args: Any?): T = item
        override fun getIfAvailable(): T = item
        override fun getIfUnique(): T = item
        override fun iterator(): MutableIterator<T> = mutableListOf(item).iterator()
        override fun stream(): java.util.stream.Stream<T> = java.util.stream.Stream.of(item)
    }
}
