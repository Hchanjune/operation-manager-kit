package io.github.hchanjune.omk.webflux.filter

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.webflux.ReactiveOperations
import io.github.hchanjune.omk.webflux.TestSupport.spanIdProvider
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals

class ReactiveSpringSecurityWebFilterTest {

    private val filter = ReactiveSpringSecurityWebFilter()

    private fun exchange() = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build())
    private fun okChain(): WebFilterChain = WebFilterChain { Mono.empty() }
    private fun managedCtx() = ManagedContext(spanIdProvider = spanIdProvider)

    @Test
    fun `sets issuer from authenticated principal`() {
        val ctx = managedCtx()
        val auth = UsernamePasswordAuthenticationToken("alice", "password")

        filter.filter(exchange(), okChain())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()

        assertEquals("alice", ctx.issuer)
    }

    @Test
    fun `sets issuer to anonymous when no security context`() {
        val ctx = managedCtx()

        filter.filter(exchange(), okChain())
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()

        assertEquals("anonymous", ctx.issuer)
    }

    @Test
    fun `sets issuer to empty string when principal is null`() {
        val ctx = managedCtx()
        val auth = UsernamePasswordAuthenticationToken(null, null)

        filter.filter(exchange(), okChain())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, ctx))
            .block()

        // null principal → getName() returns "" → issuer is set to ""
        assertEquals("", ctx.issuer)
    }

    @Test
    fun `proceeds chain even when no ManagedContext in reactor context`() {
        val auth = UsernamePasswordAuthenticationToken("bob", "pass")

        // Should not throw when ManagedContext is absent
        filter.filter(exchange(), okChain())
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
            .block()
    }

    @Test
    fun `proceeds chain without any context`() {
        filter.filter(exchange(), okChain()).block()
    }
}
