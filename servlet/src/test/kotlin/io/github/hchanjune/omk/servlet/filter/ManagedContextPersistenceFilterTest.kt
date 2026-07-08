package io.github.hchanjune.omk.servlet.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.servlet.Operations
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManagedContextPersistenceFilterTest {

    private val spanIdProvider = SpanIdProvider { "filter-span" }

    private fun contextProvider() = object : ManagedContextProvider {
        override fun provide() = ManagedContext(spanIdProvider = spanIdProvider)
    }

    private val propagation = object : TelemetryPropagationProvider {
        override fun extractTraceId(headers: (String) -> String?) = headers("X-Trace-Id")
        override fun extractParentId(headers: (String) -> String?) = headers("X-Cause-Id")
        override fun inject(traceId: String, spanId: String, setter: (String, String) -> Unit) {
            setter("X-Trace-Id", traceId)
            setter("X-Cause-Id", spanId)
        }
    }

    private fun traceProvider(id: String) = object : TraceIdProvider { override fun provideTraceId() = id }
    private fun causeProvider(id: String) = object : CausationIdProvider { override fun provideCausationId() = id }

    private inner class TrackingHook : OperationHook {
        var successCalled = false
        var failureCalled = false
        var lastException: Throwable? = null
        var lastContext: ManagedContext? = null
        override fun onSuccess(context: ManagedContext) { successCalled = true; lastContext = context }
        override fun onFailure(context: ManagedContext, exception: Throwable) {
            failureCalled = true; lastException = exception; lastContext = context
        }
    }

    private fun makeFilter(
        generateWhenMissing: Boolean = true,
        hook: OperationHook = TrackingHook(),
        excludeOptions: Boolean = false
    ) = ManagedContextPersistenceFilter(
        contextProvider = contextProvider(),
        propagationProvider = propagation,
        traceIdProvider = traceProvider("gen-trace"),
        causationIdProvider = causeProvider("gen-cause"),
        compositeHook = hook,
        generateWhenMissing = generateWhenMissing,
        excludeOptions = excludeOptions
    )

    private fun throwingChain(ex: Throwable) = FilterChain { _, _ -> throw ex }

    @BeforeTest fun setUp() { Operations.clear() }
    @AfterTest fun tearDown() { Operations.clear() }

    // ── normal dispatch: success / failure ───────────────────────────────────

    @Test
    fun `normal dispatch calls onSuccess and clears context`() {
        val hook = TrackingHook()
        makeFilter(hook = hook).doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())
        assertTrue(hook.successCalled)
        assertTrue(!Operations.hasContext)
    }

    @Test
    fun `normal dispatch with response status 500 and no exception calls onFailure`() {
        val hook = TrackingHook()
        val res = MockHttpServletResponse().apply { status = 500 }
        makeFilter(hook = hook).doFilter(MockHttpServletRequest(), res, MockFilterChain())
        assertTrue(hook.failureCalled)
        assertTrue(!hook.successCalled)
        assertEquals(OperationOutcome.SERVER_ERROR, hook.lastContext?.outcome)
    }

    @Test
    fun `normal dispatch with response status 401 calls onSuccess but classifies UNAUTHENTICATED outcome`() {
        val hook = TrackingHook()
        val res = MockHttpServletResponse().apply { status = 401 }
        makeFilter(hook = hook).doFilter(MockHttpServletRequest(), res, MockFilterChain())
        assertTrue(hook.successCalled)
        assertTrue(!hook.failureCalled)
        assertEquals(401, hook.lastContext?.statusCode)
        assertEquals(OperationOutcome.UNAUTHENTICATED, hook.lastContext?.outcome)
    }

    @Test
    fun `normal dispatch with chain exception calls onFailure and rethrows`() {
        val hook = TrackingHook()
        assertFailsWith<RuntimeException> {
            makeFilter(hook = hook).doFilter(
                MockHttpServletRequest(), MockHttpServletResponse(),
                throwingChain(RuntimeException("chain-fail"))
            )
        }
        assertTrue(hook.failureCalled)
        assertTrue(!Operations.hasContext)
    }

    // ── traceId propagation ──────────────────────────────────────────────────

    @Test
    fun `uses extracted trace header when present`() {
        var capturedTrace = ""
        val req = MockHttpServletRequest().apply {
            addHeader("X-Trace-Id", "incoming-trace")
            addHeader("X-Cause-Id", "incoming-cause")
        }
        makeFilter().doFilter(req, MockHttpServletResponse(), FilterChain { _, _ ->
            capturedTrace = Operations.context.traceId
        })
        assertEquals("incoming-trace", capturedTrace)
    }

    @Test
    fun `generates traceId when header absent and generateWhenMissing is true`() {
        var capturedTrace = ""
        makeFilter(generateWhenMissing = true).doFilter(
            MockHttpServletRequest(), MockHttpServletResponse(),
            FilterChain { _, _ -> capturedTrace = Operations.context.traceId }
        )
        assertEquals("gen-trace", capturedTrace)
    }

    @Test
    fun `uses empty traceId when header absent and generateWhenMissing is false`() {
        var capturedTrace = "not-set"
        makeFilter(generateWhenMissing = false).doFilter(
            MockHttpServletRequest(), MockHttpServletResponse(),
            FilterChain { _, _ -> capturedTrace = Operations.context.traceId }
        )
        assertEquals("", capturedTrace)
    }

    // ── IP ────────────────────────────────────────────────────────────────

    @Test
    fun `injects ip from X-Forwarded-For header when present`() {
        var capturedIp = ""
        val req = MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "203.0.113.5")
        }
        makeFilter().doFilter(req, MockHttpServletResponse(), FilterChain { _, _ ->
            capturedIp = Operations.context.ip
        })
        assertEquals("203.0.113.5", capturedIp)
    }

    @Test
    fun `takes the first entry when X-Forwarded-For has multiple hops`() {
        var capturedIp = ""
        val req = MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "203.0.113.5, 70.41.3.18, 150.172.238.178")
        }
        makeFilter().doFilter(req, MockHttpServletResponse(), FilterChain { _, _ ->
            capturedIp = Operations.context.ip
        })
        assertEquals("203.0.113.5", capturedIp)
    }

    @Test
    fun `falls back to remote address when X-Forwarded-For is absent`() {
        var capturedIp = ""
        val req = MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.1"
        }
        makeFilter().doFilter(req, MockHttpServletResponse(), FilterChain { _, _ ->
            capturedIp = Operations.context.ip
        })
        assertEquals("127.0.0.1", capturedIp)
    }

    // ── error dispatch ───────────────────────────────────────────────────────

    @Test
    fun `error dispatch with exception attribute calls onFailure with that exception`() {
        val hook = TrackingHook()
        val ex = RuntimeException("servlet-error")
        val req = MockHttpServletRequest().apply {
            setDispatcherType(DispatcherType.ERROR)
            setAttribute("jakarta.servlet.error.exception", ex)
        }
        makeFilter(hook = hook).doFilter(req, MockHttpServletResponse(), MockFilterChain())
        assertTrue(hook.failureCalled)
        assertEquals(ex, hook.lastException)
    }

    @Test
    fun `error dispatch without exception attribute calls onFailure with generated RuntimeException`() {
        val hook = TrackingHook()
        val req = MockHttpServletRequest().apply {
            setDispatcherType(DispatcherType.ERROR)
        }
        makeFilter(hook = hook).doFilter(req, MockHttpServletResponse(), MockFilterChain())
        assertTrue(hook.failureCalled)
        assertTrue(hook.lastException is RuntimeException)
    }

    // ── response header injection ────────────────────────────────────────────

    @Test
    fun `response trace header is set after filter completes`() {
        val res = MockHttpServletResponse()
        makeFilter().doFilter(MockHttpServletRequest(), res, MockFilterChain())
        assertNotNull(res.getHeader("X-Trace-Id"))
    }

    // ── excludeOptions ────────────────────────────────────────────────────────

    @Test
    fun `OPTIONS request bypasses context creation when excludeOptions is true`() {
        val hook = TrackingHook()
        val req = MockHttpServletRequest("OPTIONS", "/api/v1/auth/login/oauth/naver")
        var chainCalled = false
        makeFilter(hook = hook, excludeOptions = true).doFilter(
            req, MockHttpServletResponse(),
            FilterChain { _, _ -> chainCalled = true; assertTrue(!Operations.hasContext) }
        )
        assertTrue(chainCalled)
        assertTrue(!hook.successCalled)
        assertTrue(!hook.failureCalled)
    }

    @Test
    fun `OPTIONS request is still managed when excludeOptions is false`() {
        val hook = TrackingHook()
        val req = MockHttpServletRequest("OPTIONS", "/api/v1/auth/login/oauth/naver")
        makeFilter(hook = hook, excludeOptions = false).doFilter(req, MockHttpServletResponse(), MockFilterChain())
        assertTrue(hook.successCalled)
    }

    @Test
    fun `non-OPTIONS request is still managed when excludeOptions is true`() {
        val hook = TrackingHook()
        val req = MockHttpServletRequest("POST", "/api/v1/auth/login/oauth/naver")
        makeFilter(hook = hook, excludeOptions = true).doFilter(req, MockHttpServletResponse(), MockFilterChain())
        assertTrue(hook.successCalled)
    }
}
