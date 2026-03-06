package io.github.hchanjune.operationresult.webmvc.interceptor

import io.github.hchanjune.operationresult.core.providers.telemetry.TelemetryContextProvider
import io.github.hchanjune.operationresult.webmvc.constants.OperationMdcKeys
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Spring MVC [HandlerInterceptor] that captures the resolved controller entrypoint and stores it in MDC.
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
 * (so [HandlerMethod] is available) and before the controller method is invoked.
 *
 * ## Cleanup
 * MDC is cleared in [afterCompletion] to prevent context leakage into subsequent requests.
 *
 * ## Notes / Limitations
 * - The MDC value is only set when the handler is a [HandlerMethod].
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

        MDC.put(OperationMdcKeys.TRACE_ID, telemetry.traceId.ifBlank { "none" })
        MDC.put(OperationMdcKeys.SPAN_ID, telemetry.spanId.ifBlank { "none" })
        MDC.put(OperationMdcKeys.CAUSATION_ID, telemetry.causationId.ifBlank { "none" })

        if (handler is HandlerMethod) {
            val entryPoint = "${handler.beanType.simpleName}#${handler.method.name}"
            MDC.put(OperationMdcKeys.ENTRYPOINT, entryPoint)
            MDC.put(OperationMdcKeys.HTTP_METHOD, request.method)
            MDC.put(OperationMdcKeys.HTTP_URI, request.requestURI)
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
        MDC.remove(OperationMdcKeys.ENTRYPOINT)
        MDC.remove(OperationMdcKeys.HTTP_METHOD)
        MDC.remove(OperationMdcKeys.HTTP_URI)
        MDC.remove(OperationMdcKeys.TRACE_ID)
        MDC.remove(OperationMdcKeys.SPAN_ID)
        MDC.remove(OperationMdcKeys.CAUSATION_ID)
    }

}