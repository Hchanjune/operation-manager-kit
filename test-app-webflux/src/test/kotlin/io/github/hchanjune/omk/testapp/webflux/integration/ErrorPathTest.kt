package io.github.hchanjune.omk.testapp.webflux.integration

import io.github.hchanjune.omk.testapp.webflux.CapturingHook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPathTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @BeforeEach
    fun setUp() = capturingHook.clear()

    private fun requestError() {
        client.get().uri("/test/error").exchange().expectStatus().is5xxServerError()
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
        assertEquals("GET", ctx.method)
        assertEquals("/test/error", ctx.uri)
    }

    @Test
    fun `failure context has traceId set`() {
        requestError()
        capturingHook.awaitFailure()
        val ctx = capturingHook.lastFailure
        assertNotNull(ctx)
        assertTrue(ctx.traceId.isNotBlank())
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
