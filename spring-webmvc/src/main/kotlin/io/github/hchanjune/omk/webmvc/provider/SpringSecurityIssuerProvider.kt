package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.provider.IssuerProvider
import org.springframework.security.core.context.SecurityContextHolder

class SpringSecurityIssuerProvider: IssuerProvider {
    override fun currentIssuer(): String {
        val auth = SecurityContextHolder.getContext().authentication
        return auth?.name ?: "anonymous"
    }
}