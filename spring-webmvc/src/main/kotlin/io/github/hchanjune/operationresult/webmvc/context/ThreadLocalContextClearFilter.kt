package io.github.hchanjune.operationresult.webmvc.context

import io.github.hchanjune.operationresult.core.providers.operation.OperationContextHolderProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter

class ThreadLocalContextClearFilter(
    private val holderProvider: OperationContextHolderProvider
): OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            holderProvider.current().clear()
            MDC.clear()
        }
    }

}