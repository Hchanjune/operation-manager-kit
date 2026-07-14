package io.github.hchanjune.omk.servlet.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.servlet.Operations
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.web.filter.OncePerRequestFilter

class ManagedContextPersistenceFilter(
    private val contextProvider: ManagedContextProvider,
    private val propagationProvider: TelemetryPropagationProvider,
    private val traceIdProvider: TraceIdProvider,
    private val causationIdProvider: CausationIdProvider,
    private val compositeHook: OperationHook,
    private val generateWhenMissing: Boolean = true,
    private val excludeOptions: Boolean = false,
    private val runtime: OperationRuntime? = null,
): OncePerRequestFilter() {

    companion object {
        private const val KEY = "OMK_MANAGED_CONTEXT"
    }

    override fun shouldNotFilterErrorDispatch() = false

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        excludeOptions && request.method.equals(HttpMethod.OPTIONS.name(), ignoreCase = true)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val context: ManagedContext = request.getAttribute(KEY) as ManagedContext?
            ?: contextProvider.provide().apply {
                val extractedTraceId = propagationProvider.extractTraceId { request.getHeader(it) }
                val extractedCausationId = propagationProvider.extractParentId { request.getHeader(it) }
                this.injectTraceId(extractedTraceId ?: if (generateWhenMissing) traceIdProvider.provideTraceId() else "")
                this.injectCausationId(extractedCausationId ?: if (generateWhenMissing) causationIdProvider.provideCausationId() else "")
                this.injectProtocol("HTTP")
                this.injectType("API")
                this.injectHttpInfo(
                    uri = request.requestURI,
                    method = request.method,
                )
                this.injectIp(resolveClientIp(request))
            }.also { context ->
                context.runtime = runtime
                request.setAttribute(KEY, context)
            }

        val firstDispatch = request.dispatcherType != DispatcherType.ERROR

        try {
            if (firstDispatch) {
                Operations.initialize(context)
                try {
                    filterChain.doFilter(request, response)
                    context.injectStatusCode(response.status)
                    Operations.complete()
                    if (context.outcome == OperationOutcome.SERVER_ERROR) {
                        compositeHook.onFailure(context, context.capturedException ?: RuntimeException("HTTP ${response.status}"))
                    } else {
                        compositeHook.onSuccess(context)
                    }
                } catch (exception: Throwable) {
                    Operations.complete()
                    compositeHook.onFailure(context, exception)
                    throw exception
                }
            } else {
                val exception = request.getAttribute("jakarta.servlet.error.exception") as? Throwable
                    ?: RuntimeException("Unknown Servlet Error (Status: ${response.status})")
                context.injectStatusCode(response.status)
                Operations.applyContext(context)
                Operations.complete()
                filterChain.doFilter(request, response)
                compositeHook.onFailure(context, exception)
            }
        } finally {
            propagationProvider.inject(
                traceId = context.traceId,
                spanId = context.rootSpan?.spanId ?: context.causationId
            ) { name, value ->
                response.setHeader(name, value)
            }
            Operations.clear()
        }

    }

    /** 프록시/로드밸런서 뒤에 있을 수 있으니 X-Forwarded-For를 우선 확인하고, 없으면 소켓 주소를 쓴다. */
    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return forwardedFor ?: request.remoteAddr ?: ""
    }
}