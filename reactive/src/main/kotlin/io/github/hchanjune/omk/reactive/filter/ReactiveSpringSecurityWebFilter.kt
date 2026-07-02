package io.github.hchanjune.omk.reactive.filter

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.reactive.ReactiveOperations
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class ReactiveSpringSecurityWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication?.name ?: "anonymous" }
            .defaultIfEmpty("anonymous")
            .onErrorReturn("anonymous")
            .flatMap { issuer ->
                Mono.deferContextual { ctx ->
                    ctx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY)
                        .ifPresent { managedContext -> managedContext.injectIssuer(issuer) }
                    chain.filter(exchange)
                }
            }
    }
}
