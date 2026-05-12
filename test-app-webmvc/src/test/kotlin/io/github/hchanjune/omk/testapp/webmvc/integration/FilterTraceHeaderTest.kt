package io.github.hchanjune.omk.testapp.webmvc.integration

import io.github.hchanjune.omk.testapp.webmvc.CapturingHook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilterTraceHeaderTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val restTemplate = RestTemplate()
    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun setUp() = capturingHook.clear()

    @Test
    fun `W3C traceparent header is extracted into ManagedContext`() {
        val headers = HttpHeaders().apply {
            set("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        }
        val response = restTemplate.exchange(url("/test/ok"), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        capturingHook.awaitSuccess()

        assertEquals(HttpStatus.OK, response.statusCode)
        val ctx = capturingHook.lastSuccess
        assertNotNull(ctx)
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx!!.traceId)
        assertEquals("00f067aa0ba902b7", ctx.causationId)
    }

    @Test
    fun `traceId is auto-generated when no traceparent header is present`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val ctx = capturingHook.lastSuccess
        assertNotNull(ctx)
        assertFalse(ctx!!.traceId.isBlank(), "traceId should be auto-generated")
    }

    @Test
    fun `HTTP method and URI are injected into context`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val ctx = capturingHook.lastSuccess
        assertNotNull(ctx)
        assertEquals("GET",       ctx!!.method)
        assertEquals("/test/ok",  ctx.uri)
    }

    @Test
    fun `onSuccess hook is called exactly once per request`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()
        assertEquals(1, capturingHook.successCount)

        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()
        assertEquals(2, capturingHook.successCount)
    }
}
