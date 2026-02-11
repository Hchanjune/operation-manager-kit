package io.github.hchanjune.operationresult.webmvc.metrics

import io.github.hchanjune.operationresult.core.models.MetricsContext
import jakarta.servlet.http.HttpServletRequest

internal object MetricsBuffer {
    private const val ATTR = "operation-manager.metrics.buffer"

    fun add(request: HttpServletRequest, ctx: MetricsContext) {
        @Suppress("UNCHECKED_CAST")
        val list = (request.getAttribute(ATTR) as? MutableList<MetricsContext>)
            ?: mutableListOf<MetricsContext>().also { request.setAttribute(ATTR, it) }
        list.add(ctx)
    }

    fun drain(request: HttpServletRequest): List<MetricsContext> {
        @Suppress("UNCHECKED_CAST")
        val list = request.getAttribute(ATTR) as? MutableList<MetricsContext>?: return emptyList()
        request.removeAttribute(ATTR)
        return list.toList()
    }

}