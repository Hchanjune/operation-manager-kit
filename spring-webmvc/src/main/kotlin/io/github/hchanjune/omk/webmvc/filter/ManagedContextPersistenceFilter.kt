package io.github.hchanjune.omk.webmvc.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.webmvc.Operations
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class ManagedContextPersistenceFilter(
    private val contextProvider: ManagedContextProvider,
    private val metricsRecorder: MetricsRecorder,
    private val compositeHook: OperationHook
): OncePerRequestFilter() {

    companion object {
        private const val KEY = "OMK_MANAGED_CONTEXT"
    }

    override fun shouldNotFilterErrorDispatch() = false

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val context: ManagedContext = request.getAttribute(KEY) as ManagedContext?
            ?: contextProvider.provide().apply {
                this.injectProtocol("HTTP")
                this.injectType("API")
                this.injectHttpInfo(
                    uri = request.requestURI,
                    method = request.method,
                ).also {
                    request.setAttribute(KEY, it)
                }
            }

        val firstDispatch = request.dispatcherType != DispatcherType.ERROR

        try {
            if (firstDispatch) {
                Operations.initialize(context)
                filterChain.doFilter(request, response)
                Operations.complete()
                compositeHook.onSuccess(context)
                context.rootSpan?.let {
                    metricsRecorder.record(it)
                }
            } else {
                val exception = request.getAttribute("jakarta.servlet.error.exception") as? Throwable
                    ?: RuntimeException("Unknown Servlet Error (Status: ${response.status})")
                Operations.applyContext(context)
                Operations.complete()
                compositeHook.onFailure(context, exception)
                context.rootSpan?.let {
                    metricsRecorder.record(it)
                }
            }
        } finally {
            Operations.clear()
        }

    }
}