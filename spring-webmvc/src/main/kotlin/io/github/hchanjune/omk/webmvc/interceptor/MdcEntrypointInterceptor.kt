package io.github.hchanjune.omk.webmvc.interceptor

import io.github.hchanjune.omk.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.omk.core.models.constants.OperationKeys
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Spring MVC [org.springframework.web.servlet.HandlerInterceptor] that captures the resolved controller entrypoint and stores it in MDC.
 *
 * ## Purpose
 * Provides a consistent "entrypoint" identifier for logging and operation metadata.
 * The value is later consumed by MDC-based invocation providers (e.g. `MdcInvocationInfoProvider`).
 *
 * ## Captured MDC keys
 * - `entrypoint`: "<ControllerSimpleName>#<methodName>"
 *
 * ## Why an interceptor?
 * In Spring MVC, the interceptor is a reliable hook that runs after the handler is resolved
 * (so [org.springframework.web.method.HandlerMethod] is available) and before the controller method is invoked.
 *
 * ## Cleanup
 * MDC is cleared in [afterCompletion] to prevent context leakage into subsequent requests.
 *
 * ## Notes / Limitations
 * - The MDC value is only set when the handler is a [org.springframework.web.method.HandlerMethod].
 *   (Non-controller handlers may not populate `entrypoint`.)
 * - MDC is thread-local; async boundaries may require MDC propagation if needed.
 */
class MdcEntrypointInterceptor(
    private val telemetryProvider: TelemetryContextProvider
): HandlerInterceptor {

    /**
     * Writes the controller entrypoint into MDC before controller execution.
     *
     * @return true to continue request processing
     */
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val telemetry = telemetryProvider.current()

        MDC.put(OperationKeys.TRACE_ID, telemetry.traceId.ifBlank { "none" })
        MDC.put(OperationKeys.SPAN_ID, telemetry.spanId.ifBlank { "none" })
        MDC.put(OperationKeys.CAUSATION_ID, telemetry.causationId.ifBlank { "none" })

        if (handler is HandlerMethod) {
            val entryPoint = "${handler.beanType.simpleName}#${handler.method.name}"
            MDC.put(OperationKeys.ENTRYPOINT, entryPoint)
            MDC.put(OperationKeys.HTTP_METHOD, request.method)
            MDC.put(OperationKeys.HTTP_URI, request.requestURI)
        }
        return true
    }

    /**
     * Removes the `entrypoint` key from MDC after request completion.
     */
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        MDC.remove(OperationKeys.ENTRYPOINT)
        MDC.remove(OperationKeys.HTTP_METHOD)
        MDC.remove(OperationKeys.HTTP_URI)
        MDC.remove(OperationKeys.TRACE_ID)
        MDC.remove(OperationKeys.SPAN_ID)
        MDC.remove(OperationKeys.CAUSATION_ID)
    }

}