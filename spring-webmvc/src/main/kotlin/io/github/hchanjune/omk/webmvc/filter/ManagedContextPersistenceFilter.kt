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

    override fun shouldNotFilterErrorDispatch() = false

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {

        var isSuccess = false
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
            isSuccess = true
        } catch (exception: Throwable) {
            compositeHook.onFailure(context, exception)
            throw exception
        } finally {
            Operations.complete()
            if (isSuccess) {
                compositeHook.onSuccess(context)
            }
            context.rootSpan?.let {
                metricsRecorder.record(it)
            }
            Operations.clear()
        }
    }
}