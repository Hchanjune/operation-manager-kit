package io.github.hchanjune.operationresult.webmvc.interceptor

import io.github.hchanjune.operationresult.webmvc.constants.OperationMdcKeys
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.lang.Exception

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
class MdcEntrypointInterceptor: HandlerInterceptor {

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

        if (handler is HandlerMethod) {
            val controller = handler.beanType.simpleName
            val method = handler.method.name

            MDC.put(OperationMdcKeys.ENTRYPOINT, "$controller#$method")
        }

        return true
    }

    /**
     * Removes the `entrypoint` key from MDC after request completion.
     */
    override fun afterCompletion(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        handler: Any?,
        ex: Exception?
    ) {
        MDC.remove(OperationMdcKeys.ENTRYPOINT)
    }

}