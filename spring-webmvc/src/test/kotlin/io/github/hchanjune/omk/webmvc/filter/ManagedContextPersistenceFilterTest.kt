package io.github.hchanjune.omk.webmvc.filter

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TelemetryPropagationProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.webmvc.Operations
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
        hook: OperationHook = TrackingHook()
    ) = ManagedContextPersistenceFilter(
        contextProvider = contextProvider(),
        propagationProvider = propagation,
        traceIdProvider = traceProvider("gen-trace"),
        causationIdProvider = causeProvider("gen-cause"),
        compositeHook = hook,
        generateWhenMissing = generateWhenMissing
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
}
