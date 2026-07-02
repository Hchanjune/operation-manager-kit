package io.github.hchanjune.omk.servlet.filter

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringSecurityConfigurationFilterTest {

    private val spanIdProvider = SpanIdProvider { "sec-span" }

    @AfterTest
    fun tearDown() { Operations.clear() }

    @Test
    fun `injects issuer into context when context is present`() {
        val ctx = ManagedContext(spanIdProvider = spanIdProvider)
        Operations.applyContext(ctx)

        val filter = SpringSecurityConfigurationFilter(IssuerProvider { "current-user" })
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())

        assertEquals("current-user", ctx.issuer)
    }

    @Test
    fun `skips issuer injection and continues chain when no context is present`() {
        val filter = SpringSecurityConfigurationFilter(IssuerProvider { "current-user" })
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())
        assertTrue(!Operations.hasContext)
    }
}
