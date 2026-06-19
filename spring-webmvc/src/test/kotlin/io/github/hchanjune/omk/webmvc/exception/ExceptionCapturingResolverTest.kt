package io.github.hchanjune.omk.webmvc.exception

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webmvc.Operations
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExceptionCapturingResolverTest {

    private val resolver = ExceptionCapturingResolver()
    private val spanIdProvider = SpanIdProvider { "resolver-test-span" }
    private val request: HttpServletRequest = MockHttpServletRequest()
    private val response: HttpServletResponse = MockHttpServletResponse()

    @BeforeTest
    fun setUp() = Operations.clear()

    @AfterTest
    fun tearDown() = Operations.clear()

    @Test
    fun `order is highest precedence so it runs before the real resolvers`() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, resolver.order)
    }

    @Test
    fun `always returns null so the real resolver chain still produces a response`() {
        Operations.applyContext(ManagedContext(spanIdProvider = spanIdProvider))
        val result = resolver.resolveException(request, response, null, IllegalStateException("boom"))
        assertNull(result)
    }

    @Test
    fun `records the exception onto the current context when one exists`() {
        val ctx = ManagedContext(spanIdProvider = spanIdProvider)
        Operations.applyContext(ctx)
        val ex = IllegalStateException("boom")

        resolver.resolveException(request, response, null, ex)

        assertEquals(ex, ctx.capturedException)
    }

    @Test
    fun `does not throw when no context is present`() {
        assertTrue(!Operations.hasContext)
        val result = resolver.resolveException(request, response, null, IllegalStateException("boom"))
        assertNull(result)
    }
}
