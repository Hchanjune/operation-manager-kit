package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.defaults.MetricTagOption
import io.github.hchanjune.operationresult.core.models.MetricsContext
import io.github.hchanjune.operationresult.core.providers.MetricsEnricher
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping

/**
 * WebMVC-specific MetricsEnricher.
 *
 * Adds low-cardinality HTTP tags such as:
 * - http.method
 * - http.route (best matching URI template)
 * - result
 */
class WebMvcMetricsEnricher: MetricsEnricher {
    override fun enrich(context: MetricsContext): MetricsContext {
        val outcome = context.outcome

        val request = currentRequestOrNull()

        return context.withTags {
            // Outcome tags
            put(MetricTagOption.RESULT, outcome?.result?.name?.lowercase())
            put(MetricTagOption.STATUS_GROUP, outcome?.statusGroup?.name?.lowercase())
            put(MetricTagOption.EXCEPTION, outcome?.exception)

            // HTTP tags (adapter-specific)
            put(MetricTagOption.HTTP_METHOD, request?.method)
            put(MetricTagOption.HTTP_ROUTE, request?.bestMatchingRoute())
        }
    }

    private fun currentRequestOrNull(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun HttpServletRequest.bestMatchingRoute(): String? =
        getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
}