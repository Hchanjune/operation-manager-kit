package io.github.hchanjune.omk.webmvc.filter

import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.webmvc.Operations
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.filter.OncePerRequestFilter

class SpringSecurityConfigurationFilter(
    private val issuerProvider: IssuerProvider
): OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        logger.info((">>> IssuerInjection: ${issuerProvider.currentIssuer()}"))
        Operations.context.injectIssuer(issuerProvider.currentIssuer())
        filterChain.doFilter(request, response)
    }
}