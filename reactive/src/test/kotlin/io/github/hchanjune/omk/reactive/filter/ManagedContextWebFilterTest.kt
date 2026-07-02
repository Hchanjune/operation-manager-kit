package io.github.hchanjune.omk.reactive.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.ReactiveOperations
import io.github.hchanjune.omk.reactive.TestSupport.spanIdProvider
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManagedContextWebFilterTest {

    private fun contextProvider() = object : ManagedContextProvider {
        override fun provide() = ManagedContext(spanIdProvider = spanIdProvider)
    }

    private val w3cPropagation = object : TelemetryPropagationProvider {
        override fun extractTraceId(h: (String) -> String?) = h("traceparent")?.split("-")?.getOrNull(1)
        override fun extractParentId(h: (String) -> String?) = h("traceparent")?.split("-")?.getOrNull(2)
        override fun inject(traceId: String, spanId: String, setter: (String, String) -> Unit) {
            setter("traceparent", "00-$traceId-$spanId-01")
        }
    }

    private fun traceProvider(id: String = "gen-trace") = object : TraceIdProvider { override fun provideTraceId() = id }
    private fun causeProvider(id: String = "gen-cause") = object : CausationIdProvider { override fun provideCausationId() = id }
    private fun issuerProvider(issuer: String = "anonymous") = IssuerProvider { issuer }

    private inner class TrackingHook : OperationHook {
        var successCalled = false
        var failureCalled = false
        override fun onSuccess(context: ManagedContext) { successCalled = true }
        override fun onFailure(context: ManagedContext, exception: Throwable) { failureCalled = true }
    }

    private fun makeFilter(
        generateWhenMissing: Boolean = true,
        hook: OperationHook = TrackingHook(),
        issuer: String = "anonymous",
        excludeOptions: Boolean = false
    ) = ManagedContextWebFilter(
        contextProvider = contextProvider(),
        propagationProvider = w3cPropagation,
        traceIdProvider = traceProvider(),
        causationIdProvider = causeProvider(),
        compositeHook = hook,
        issuerProvider = issuerProvider(issuer),
        generateWhenMissing = generateWhenMissing,
        excludeOptions = excludeOptions
    )

    private fun okChain(): WebFilterChain = WebFilterChain { Mono.empty() }

    private fun errorChain(): WebFilterChain = WebFilterChain {
        Mono.error(RuntimeException("chain-error"))
    }

    private fun exchange(path: String = "/test", traceparent: String? = null): MockServerWebExchange {
        val req = MockServerHttpRequest.get(path).let {
            if (traceparent != null) it.header("traceparent", traceparent) else it
        }
        return MockServerWebExchange.from(req.build())
    }

    // ── Basic filter behavior ─────────────────────────────────────────────────

    // beforeCommit fires when response.setComplete() is called
    private fun runFilter(filter: ManagedContextWebFilter, exchange: MockServerWebExchange, chain: WebFilterChain) {
        filter.filter(exchange, chain)
            .then(exchange.response.setComplete())
            .block()
    }

    @Test
    fun `filter proceeds chain and completes`() {
        val filter = makeFilter()
        val ex = exchange()
        StepVerifier.create(
            filter.filter(ex, okChain()).then(ex.response.setComplete())
        ).verifyComplete()
    }

    @Test
    fun `filter calls onSuccess hook when chain succeeds`() {
        val hook = TrackingHook()
        val filter = makeFilter(hook = hook)
        runFilter(filter, exchange(), okChain())
        assertTrue(hook.successCalled)
    }

    @Test
    fun `filter calls onFailure hook when chain returns 5xx`() {
        val hook = TrackingHook()
        val filter = makeFilter(hook = hook)
        val ex = exchange()
        runFilter(filter, ex, WebFilterChain {
            it.response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
            Mono.empty()
        })
        assertTrue(hook.failureCalled)
    }

    @Test
    fun `filter calls onSuccess but classifies 401 as UNAUTHENTICATED outcome`() {
        var capturedCtx: ManagedContext? = null
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { capturedCtx = context }
        }
        val filter = makeFilter(hook = hook)
        val ex = exchange()
        runFilter(filter, ex, WebFilterChain {
            it.response.statusCode = HttpStatus.UNAUTHORIZED
            Mono.empty()
        })
        assertNotNull(capturedCtx)
        assertEquals(401, capturedCtx?.statusCode)
        assertEquals(OperationOutcome.UNAUTHENTICATED, capturedCtx?.outcome)
    }

    private fun filterWithCapture(
        generateWhenMissing: Boolean = true,
        issuer: String = "anonymous",
        onSuccess: (ManagedContext) -> Unit = {}
    ): ManagedContextWebFilter = ManagedContextWebFilter(
        contextProvider = contextProvider(),
        propagationProvider = w3cPropagation,
        traceIdProvider = traceProvider("fixed-trace"),
        causationIdProvider = causeProvider("fixed-cause"),
        compositeHook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) = onSuccess(context)
        },
        issuerProvider = issuerProvider(issuer),
        generateWhenMissing = generateWhenMissing
    )

    @Test
    fun `filter generates traceId when header absent and generateWhenMissing=true`() {
        var capturedCtx: ManagedContext? = null
        val filter = filterWithCapture { capturedCtx = it }
        val ex = exchange()
        runFilter(filter, ex, okChain())
        assertNotNull(capturedCtx)
        assertEquals("fixed-trace", capturedCtx!!.traceId)
    }

    @Test
    fun `filter extracts traceId from W3C traceparent header`() {
        var capturedCtx: ManagedContext? = null
        val filter = filterWithCapture { capturedCtx = it }
        val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        runFilter(filter, exchange(traceparent = traceparent), okChain())
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", capturedCtx?.traceId)
        assertEquals("00f067aa0ba902b7",                  capturedCtx?.causationId)
    }

    @Test
    fun `filter injects issuer from issuerProvider`() {
        var capturedCtx: ManagedContext? = null
        val filter = filterWithCapture(issuer = "john.doe") { capturedCtx = it }
        runFilter(filter, exchange(), okChain())
        assertEquals("john.doe", capturedCtx?.issuer)
    }

    @Test
    fun `filter places ManagedContext in Reactor context`() {
        val filter = makeFilter()
        val ex = exchange()
        var contextFound = false
        runFilter(filter, ex, WebFilterChain { exchange ->
            Mono.deferContextual { ctx ->
                contextFound = ctx.hasKey(ReactiveOperations.CONTEXT_KEY)
                Mono.empty()
            }
        })
        assertTrue(contextFound)
    }

    @Test
    fun `filter injects HTTP method and URI into context`() {
        var capturedCtx: ManagedContext? = null
        val filter = filterWithCapture { capturedCtx = it }
        runFilter(filter, exchange("/api/orders"), okChain())
        assertEquals("/api/orders", capturedCtx?.uri)
        assertEquals("GET",         capturedCtx?.method)
    }

    @Test
    fun `filter sets empty strings when generateWhenMissing=false and no headers`() {
        var capturedCtx: ManagedContext? = null
        val filter = filterWithCapture(generateWhenMissing = false) { capturedCtx = it }
        runFilter(filter, exchange(), okChain())
        assertEquals("", capturedCtx?.traceId)
        assertEquals("", capturedCtx?.causationId)
    }

    // ── excludeOptions ────────────────────────────────────────────────────────

    private fun optionsExchange(path: String = "/test"): MockServerWebExchange =
        MockServerWebExchange.from(MockServerHttpRequest.options(path).build())

    @Test
    fun `OPTIONS request bypasses context creation when excludeOptions is true`() {
        val hook = TrackingHook()
        val filter = makeFilter(hook = hook, excludeOptions = true)
        var contextFound = true
        runFilter(filter, optionsExchange(), WebFilterChain { exchange ->
            Mono.deferContextual { ctx ->
                contextFound = ctx.hasKey(ReactiveOperations.CONTEXT_KEY)
                Mono.empty()
            }
        })
        assertTrue(!contextFound)
        assertTrue(!hook.successCalled)
        assertTrue(!hook.failureCalled)
    }

    @Test
    fun `OPTIONS request is still managed when excludeOptions is false`() {
        val hook = TrackingHook()
        val filter = makeFilter(hook = hook, excludeOptions = false)
        runFilter(filter, optionsExchange(), okChain())
        assertTrue(hook.successCalled)
    }

    @Test
    fun `non-OPTIONS request is still managed when excludeOptions is true`() {
        val hook = TrackingHook()
        val filter = makeFilter(hook = hook, excludeOptions = true)
        runFilter(filter, exchange(), okChain())
        assertTrue(hook.successCalled)
    }
}
