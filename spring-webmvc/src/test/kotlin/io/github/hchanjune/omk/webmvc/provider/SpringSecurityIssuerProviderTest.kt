package io.github.hchanjune.omk.webmvc.provider

import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpringSecurityIssuerProviderTest {

    private val provider = SpringSecurityIssuerProvider()

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun fakeAuth(name: String): Authentication = object : Authentication {
        override fun getName() = name
        override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
        override fun getCredentials(): Any? = null
        override fun getDetails(): Any? = null
        override fun getPrincipal(): Any? = null
        override fun isAuthenticated() = true
        override fun setAuthenticated(isAuthenticated: Boolean) {}
    }

    @Test
    fun `returns anonymous when authentication is null`() {
        SecurityContextHolder.clearContext()
        assertEquals("anonymous", provider.currentIssuer())
    }

    @Test
    fun `returns anonymous when auth name is anonymousUser`() {
        SecurityContextHolder.getContext().authentication = fakeAuth("anonymousUser")
        assertEquals("anonymous", provider.currentIssuer())
    }

    @Test
    fun `returns actual name when auth name is a real user`() {
        SecurityContextHolder.getContext().authentication = fakeAuth("john.doe")
        assertEquals("john.doe", provider.currentIssuer())
    }
}
