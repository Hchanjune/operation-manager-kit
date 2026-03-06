package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.providers.metric.MetricOutcomeClassifier
import io.github.hchanjune.operationresult.core.providers.metric.MetricsEnricher
import io.github.hchanjune.operationresult.core.providers.metric.MetricsRecorder
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

class MetricsFlushFilter(
    private val delegate: MetricsRecorder,
    private val classifier: MetricOutcomeClassifier,
    private val enricher: MetricsEnricher
) : OncePerRequestFilter() {

    private val flushedAttr = MetricsFlushFilter::class.java.name + ".FLUSHED"
    private val thrownAttr = MetricsFlushFilter::class.java.name + ".THROWN"
    private val originalRouteAttr = MetricsFlushFilter::class.java.name + ".ORIGINAL_ROUTE"

    override fun shouldNotFilterAsyncDispatch(): Boolean = true
    override fun shouldNotFilterErrorDispatch(): Boolean = false

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        var shouldFlush = true

        try {
            chain.doFilter(request, response)
        } catch (ex: Throwable) {
            request.setAttribute(thrownAttr, true)
            throw ex
        } finally {

            // already flushed?
            if (request.getAttribute(flushedAttr) == true) {
                shouldFlush = false
            }

            val isErrorDispatch = request.dispatcherType == DispatcherType.ERROR
            val thrown = request.getAttribute(thrownAttr) == true

            // exception thrown → skip REQUEST flush, wait for ERROR flush
            if (thrown && !isErrorDispatch) {
                shouldFlush = false
            }

            if (!isErrorDispatch) {
                val routeNow =
                    request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
                if (!routeNow.isNullOrBlank()) {
                    request.setAttribute(originalRouteAttr, routeNow)
                }
            }

            if (shouldFlush) {
                request.setAttribute(flushedAttr, true)

                val buffered = MetricsBuffer.drain(request)
                if (buffered.isNotEmpty()) {

                    val status = response.status
                    val error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) as? Throwable

                    buffered.forEach { ctx ->
                        val finalOutcome = classifier.classify(status, error)
                        val finalizedContext = ctx.copy(outcome = finalOutcome)
                            .let { enricher.enrich(it) }
                        delegate.record(finalizedContext)
                    }
                }
            }
        }
    }


}
