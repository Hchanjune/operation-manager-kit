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
 *
 * ### Notes:
 *
 *  Currently uses RequestContextHolder to get httpServletRequest.
 *
 *  Which means, `ConnectionLost` represents:
 *
 *  - Thread Changed
 *  - Async Process
 *  - None Http Operation (batch, scheduled etc...)
 *
 *
 */
class WebMvcMetricsEnricher: MetricsEnricher {
    override fun enrich(context: MetricsContext): MetricsContext {
        val outcome = context.outcome
        val descriptor = context.descriptor
        val request = currentRequestOrNull()

        return context.withTags {
            // Outcome tags
            put(MetricTagOption.RESULT, outcome?.result?.name?.lowercase())
            put(MetricTagOption.STATUS_GROUP, outcome?.statusGroup?.name?.lowercase())
            put(MetricTagOption.EXCEPTION, outcome?.exception?: "none")

            // Descriptor
            put(MetricTagOption.OPERATION, descriptor?.operation?: "none")
            put(MetricTagOption.USE_CASE, descriptor?.useCase?: "none")
            put(MetricTagOption.EVENT, descriptor?.event?: "none")

            // HTTP tags (adapter-specific)
            put(MetricTagOption.HTTP_METHOD, request?.method?: "ConnectionLost")
            put(MetricTagOption.HTTP_ROUTE, request?.bestMatchingRoute()?: "ConnectionLost")
        }
    }

    private fun currentRequestOrNull(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun HttpServletRequest.bestMatchingRoute(): String? =
        getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
}