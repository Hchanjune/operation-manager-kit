package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.defaults.DefaultMetricsContextFactory
import io.github.hchanjune.operationresult.core.models.MetricName
import io.github.hchanjune.operationresult.core.models.MetricsContext
import io.github.hchanjune.operationresult.core.providers.MetricsContextFactory
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
class WebMvcMetricsContextFactory(
    private val metricName: MetricName = MetricName("operation.duration")
) : MetricsContextFactory {

    override fun create(): MetricsContext {
        val base = DefaultMetricsContextFactory.create()
            .copy(name = metricName)

        val req = currentRequestOrNull()

        return base.withTags {
            put("http.method", req?.method)
            put("http.uri", req?.bestMatchingPatternOrNull())
        }
    }

    private fun currentRequestOrNull(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun HttpServletRequest.bestMatchingPatternOrNull(): String? =
        getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
}