package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.models.MetricsContext
import io.github.hchanjune.operationresult.core.providers.MetricsRecorder
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class RoutingWebMvcMetricsRecorder(
    private val delegate: MetricsRecorder
): MetricsRecorder {
    override fun record(context: MetricsContext) {
        val request = currentRequestOrNull()
        if (request == null) {
            // non-web (tests, batch jobs, async threads without request context)
            println("non web request")
            delegate.record(context)
            return
        }
        println("web request")
        // web request: buffer and flush later with final status
        MetricsBuffer.add(request, context)
    }
    private fun currentRequestOrNull(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
}