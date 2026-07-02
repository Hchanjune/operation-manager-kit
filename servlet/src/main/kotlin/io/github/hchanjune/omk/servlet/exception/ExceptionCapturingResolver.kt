package io.github.hchanjune.omk.servlet.exception

import io.github.hchanjune.omk.servlet.Operations
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView

/**
 * Runs before ExceptionHandlerExceptionResolver so it sees the exception
 * @ExceptionHandler/@ControllerAdvice is about to convert into a response.
 * Always returns null so the real resolver chain still produces that response;
 * this only records the exception onto the ManagedContext for observability.
 */
class ExceptionCapturingResolver : HandlerExceptionResolver, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun resolveException(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any?,
        ex: Exception
    ): ModelAndView? {
        if (Operations.hasContext) {
            Operations.context.recordException(ex)
        }
        return null
    }

}
