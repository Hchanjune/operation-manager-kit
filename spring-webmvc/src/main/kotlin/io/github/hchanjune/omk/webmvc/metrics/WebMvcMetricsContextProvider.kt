package io.github.hchanjune.omk.webmvc.metrics

import io.github.hchanjune.omk.core.models.context.MetricsContext
import io.github.hchanjune.omk.core.models.metric.MetricName
import io.github.hchanjune.omk.core.providers.metric.MetricsContextProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping

/**
 * WebMVC-specific MetricsContextFactory.
 *
 * Enriches tags with HTTP information:
 * - method
 * - uri_template (best matching pattern)
 */
class WebMvcMetricsContextProvider(
    private val metricName: MetricName = MetricName("operation.duration")
) : MetricsContextProvider {

    override fun current(): MetricsContext {
        val base = MetricsContext(name = metricName)

        val req = currentRequestOrNull()

        return base.withTags {
            put("http.method", req?.method ?: "UNKNOWN")
            put("http.uri", req?.bestMatchingPatternOrNull() ?: "UNKNOWN")
        }
    }

    private fun currentRequestOrNull(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun HttpServletRequest.bestMatchingPatternOrNull(): String? =
        getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
}