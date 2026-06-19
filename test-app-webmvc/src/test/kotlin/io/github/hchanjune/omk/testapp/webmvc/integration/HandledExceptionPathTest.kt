package io.github.hchanjune.omk.testapp.webmvc.integration

import io.github.hchanjune.omk.testapp.webmvc.CapturingHook
import io.github.hchanjune.omk.testapp.webmvc.SampleClientException
import io.github.hchanjune.omk.testapp.webmvc.SampleServerException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Reproduces the bug where a @RestControllerAdvice (like PlatformExceptionHandler) converts an
 * exception into a normal response before the OMK filter ever sees it, so the original exception
 * never reached the hooks. ExceptionCapturingResolver is supposed to record it regardless.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandledExceptionPathTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val restTemplate = RestTemplate()
    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun setUp() = capturingHook.clear()

    @Test
    fun `a 4xx produced by a @ExceptionHandler still routes through onSuccess, not onFailure`() {
        callClientErrorEndpoint()
        capturingHook.awaitSuccess()

        assertEquals(1, capturingHook.successCount)
        assertEquals(0, capturingHook.failureCount)
    }

    @Test
    fun `the real exception caught by @ExceptionHandler is visible on the success context`() {
        callClientErrorEndpoint()
        capturingHook.awaitSuccess()

        val captured = capturingHook.lastSuccess?.capturedException
        assertNotNull(captured)
        assertIs<SampleClientException>(captured)
        assertEquals("client-error", captured.message)
    }

    @Test
    fun `a 5xx produced by a @ExceptionHandler routes through onFailure with the real exception, not a synthetic one`() {
        callServerErrorEndpoint()
        capturingHook.awaitFailure()

        val ex = capturingHook.lastException
        assertNotNull(ex)
        assertIs<SampleServerException>(ex)
        assertEquals("server-error", ex.message)
    }

    private fun callClientErrorEndpoint(): HttpStatus {
        return try {
            restTemplate.getForEntity(url("/test/handled-client-error"), String::class.java).statusCode as HttpStatus
        } catch (ex: HttpClientErrorException) {
            ex.statusCode as HttpStatus
        }
    }

    private fun callServerErrorEndpoint() {
        try {
            restTemplate.getForEntity(url("/test/handled-server-error"), String::class.java)
        } catch (_: Exception) { /* 500 is expected */ }
    }
}
