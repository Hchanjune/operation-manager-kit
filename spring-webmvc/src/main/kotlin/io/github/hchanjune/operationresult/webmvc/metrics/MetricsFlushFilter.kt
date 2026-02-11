package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.defaults.MetricTagOption
import io.github.hchanjune.operationresult.core.models.MetricOutcome
import io.github.hchanjune.operationresult.core.providers.MetricsRecorder
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

class MetricsFlushFilter(private val delegate: MetricsRecorder) : OncePerRequestFilter() {

    private val flushedAttr = MetricsFlushFilter::class.java.name + ".FLUSHED"
    private val thrownAttr = MetricsFlushFilter::class.java.name + ".THROWN"

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

            if (shouldFlush) {
                request.setAttribute(flushedAttr, true)

                val buffered = MetricsBuffer.drain(request)
                if (buffered.isNotEmpty()) {

                    val status = response.status
                    val route =
                        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String

                    val statusGroup = when (status) {
                        in 200..299 -> MetricOutcome.StatusGroup.S2XX
                        in 300..399 -> MetricOutcome.StatusGroup.S3XX
                        in 400..499 -> MetricOutcome.StatusGroup.S4XX
                        in 500..599 -> MetricOutcome.StatusGroup.S5XX
                        else -> null
                    }

                    buffered.forEach { ctx ->
                        val enriched = ctx.withTags {
                            put(MetricTagOption.HTTP_STATUS, status.toString())
                            put(MetricTagOption.STATUS_GROUP, statusGroup?.name?.lowercase())
                            put(MetricTagOption.HTTP_METHOD, request.method)
                            put(MetricTagOption.HTTP_ROUTE, route)
                        }
                        delegate.record(enriched)
                    }
                }
            }
        }
    }


}
