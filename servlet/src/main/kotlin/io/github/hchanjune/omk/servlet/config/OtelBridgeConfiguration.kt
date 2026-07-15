package io.github.hchanjune.omk.servlet.config

import io.github.hchanjune.omk.core.bridge.SpanBridge
import io.github.hchanjune.omk.otel.OtelSpanBridge
import io.opentelemetry.api.trace.Tracer
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the live OTel span bridge when a Tracer bean exists — OMK spans then start as
 * real OTel spans (ids adopted, current-context set per thread) instead of being replayed
 * after the fact. No Tracer/no OTel on the classpath → no bean → OMK runs self-contained.
 */
@Configuration
@ConditionalOnClass(name = ["io.opentelemetry.api.trace.Tracer"])
internal class OtelBridgeConfiguration {

    @Bean
    @ConditionalOnBean(type = ["io.opentelemetry.api.trace.Tracer"])
    @ConditionalOnProperty(
        prefix = "operation-manager.servlet.otel",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true
    )
    fun spanBridge(tracer: Tracer): SpanBridge =
        // Servlet aspects start and end each span on the same thread in LIFO order,
        // so thread-bound current-context scopes are safe here.
        OtelSpanBridge(tracer, makeCurrent = true)
}
