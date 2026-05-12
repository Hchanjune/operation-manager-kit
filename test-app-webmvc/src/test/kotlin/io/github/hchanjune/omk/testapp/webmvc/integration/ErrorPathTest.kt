package io.github.hchanjune.omk.testapp.webmvc.integration

import io.github.hchanjune.omk.testapp.webmvc.CapturingHook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPathTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val restTemplate = RestTemplate()
    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun setUp() = capturingHook.clear()

    private fun requestError() {
        try {
            restTemplate.getForEntity(url("/test/error"), String::class.java)
        } catch (_: Exception) { /* 500 is expected */ }
    }

    @Test
    fun `error endpoint triggers onFailure hook`() {
        requestError()
        capturingHook.awaitFailure()
        assertEquals(1, capturingHook.failureCount)
    }

    @Test
    fun `failure context contains HTTP method and URI`() {
        requestError()
        capturingHook.awaitFailure()
        val ctx = capturingHook.lastFailure
        assertNotNull(ctx)
        assertEquals("GET", ctx!!.method)
        assertEquals("/test/error", ctx.uri)
    }

    @Test
    fun `failure context has traceId set`() {
        requestError()
        capturingHook.awaitFailure()
        val ctx = capturingHook.lastFailure
        assertNotNull(ctx)
        assertTrue(ctx!!.traceId.isNotBlank())
    }

    @Test
    fun `captured exception is non-null`() {
        requestError()
        capturingHook.awaitFailure()
        assertNotNull(capturingHook.lastException)
    }

    @Test
    fun `success hook is not called on error path`() {
        requestError()
        capturingHook.awaitFailure()
        assertEquals(0, capturingHook.successCount)
    }
}
