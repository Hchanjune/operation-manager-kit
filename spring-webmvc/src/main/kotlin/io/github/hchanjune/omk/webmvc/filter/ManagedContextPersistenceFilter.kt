package io.github.hchanjune.omk.webmvc.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.metric.MetricsRecorder
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.webmvc.Operations
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class ManagedContextPersistenceFilter(
    private val contextProvider: ManagedContextProvider,
    private val metricsRecorder: MetricsRecorder,
    private val compositeHook: OperationHook
): OncePerRequestFilter() {

    override fun shouldNotFilterErrorDispatch() = true

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        val context = contextProvider.provide().apply {
            this.injectProtocol("HTTP")
            this.injectType("API")
            this.injectHttpInfo(
                uri = request.requestURI,
                method = request.method,
            )
        }

        try {
            Operations.initialize(context)
            filterChain.doFilter(request, response)
        } finally {
            Operations.complete()
            val isErrorStatus = response.status >= 400

            if (isErrorStatus) {
                val exception = request.getAttribute("jakarta.servlet.error.exception") as? Throwable
                    ?: RuntimeException("HTTP Error ${response.status}")
                compositeHook.onFailure(context, exception)
            } else {
                compositeHook.onSuccess(context)
            }

            context.rootSpan?.let {
                metricsRecorder.record(it)
            }

            Operations.clear()
        }
    }
}