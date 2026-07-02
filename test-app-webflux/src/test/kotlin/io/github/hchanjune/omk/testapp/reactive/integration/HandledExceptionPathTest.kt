package io.github.hchanjune.omk.testapp.reactive.integration

import io.github.hchanjune.omk.testapp.reactive.CapturingHook
import io.github.hchanjune.omk.testapp.reactive.SampleClientException
import io.github.hchanjune.omk.testapp.reactive.SampleServerException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Reproduces the bug where a @RestControllerAdvice converts an exception into a normal response
 * before ManagedContextWebFilter's beforeCommit callback ever sees the original exception object.
 * ManagedControllerAspect is supposed to record it via ctx.recordException() regardless.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandledExceptionPathTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @BeforeEach
    fun setUp() = capturingHook.clear()

    @Test
    fun `a 4xx produced by a @ExceptionHandler still routes through onSuccess, not onFailure`() {
        client.get().uri("/test/handled-client-error").exchange().expectStatus().isBadRequest
        capturingHook.awaitSuccess()

        assertEquals(1, capturingHook.successCount)
        assertEquals(0, capturingHook.failureCount)
    }

    @Test
    fun `the real exception caught by @ExceptionHandler is visible on the success context`() {
        client.get().uri("/test/handled-client-error").exchange().expectStatus().isBadRequest
        capturingHook.awaitSuccess()

        val captured = capturingHook.lastSuccess?.capturedException
        assertNotNull(captured)
        assertIs<SampleClientException>(captured)
        assertEquals("client-error", captured.message)
    }

    @Test
    fun `a 5xx produced by a @ExceptionHandler routes through onFailure with the real exception, not a synthetic one`() {
        client.get().uri("/test/handled-server-error").exchange().expectStatus().is5xxServerError()
        capturingHook.awaitFailure()

        val ex = capturingHook.lastException
        assertNotNull(ex)
        assertIs<SampleServerException>(ex)
        assertEquals("server-error", ex.message)
    }
}
