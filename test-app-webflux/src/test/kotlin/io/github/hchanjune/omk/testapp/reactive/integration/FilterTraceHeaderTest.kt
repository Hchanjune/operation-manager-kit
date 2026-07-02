package io.github.hchanjune.omk.testapp.reactive.integration

import io.github.hchanjune.omk.testapp.reactive.CapturingHook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilterTraceHeaderTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @BeforeEach
    fun setUp() = capturingHook.clear()

    @Test
    fun `W3C traceparent header is extracted into ManagedContext`() {
        client.get().uri("/test/ok")
            .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
            .exchange()
            .expectStatus().isOk()
        capturingHook.awaitSuccess()

        val ctx = capturingHook.lastSuccess
        assertNotNull(ctx)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.traceId)
        assertEquals("00f067aa0ba902b7", ctx.causationId)
    }

    @Test
    fun `traceId is auto-generated when no traceparent header is present`() {
        client.get().uri("/test/ok").exchange().expectStatus().isOk()
        capturingHook.awaitSuccess()

        val ctx = capturingHook.lastSuccess
        assertNotNull(ctx)
        assertFalse(ctx.traceId.isBlank(), "traceId should be auto-generated")
    }

    @Test
    fun `HTTP method and URI are injected into context`() {
        client.get().uri("/test/ok").exchange().expectStatus().isOk()
        capturingHook.awaitSuccess()

        val ctx = capturingHook.lastSuccess
        assertNotNull(ctx)
        assertEquals("GET",      ctx.method)
        assertEquals("/test/ok", ctx.uri)
    }

    @Test
    fun `onSuccess hook is called exactly once per request`() {
        client.get().uri("/test/ok").exchange().expectStatus().isOk()
        capturingHook.awaitSuccess()
        assertEquals(1, capturingHook.successCount)

        client.get().uri("/test/ok").exchange().expectStatus().isOk()
        capturingHook.awaitSuccess()
        assertEquals(2, capturingHook.successCount)
    }
}
