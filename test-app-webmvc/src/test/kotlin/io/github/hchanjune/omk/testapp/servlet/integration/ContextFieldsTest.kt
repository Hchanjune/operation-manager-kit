package io.github.hchanjune.omk.testapp.servlet.integration

import io.github.hchanjune.omk.core.contants.ManagedProtocolType
import io.github.hchanjune.omk.testapp.servlet.CapturingHook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContextFieldsTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val restTemplate = RestTemplate()
    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun setUp() = capturingHook.clear()

    private fun doRequest(): Unit {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()
    }

    @Test
    fun `context protocol is HTTP`() {
        doRequest()
        assertEquals(ManagedProtocolType.HTTP, capturingHook.lastSuccess!!.protocol)
    }

    @Test
    fun `context type is API`() {
        doRequest()
        assertEquals("API", capturingHook.lastSuccess!!.type)
    }

    @Test
    fun `context uri and method are injected`() {
        doRequest()
        val ctx = capturingHook.lastSuccess!!
        assertEquals("/test/ok", ctx.uri)
        assertEquals("GET", ctx.method)
    }

    @Test
    fun `entrypoint is set to controller class name by ManagedControllerAspect`() {
        doRequest()
        assertEquals("SampleController", capturingHook.lastSuccess!!.entrypoint)
    }

    @Test
    fun `operation and useCase are set by ManagedOperationAspect`() {
        doRequest()
        val ctx = capturingHook.lastSuccess!!
        assertEquals("ProcessSample", ctx.operation)
        assertEquals("TestUseCase", ctx.useCase)
    }

    @Test
    fun `context has non-blank traceId after request`() {
        doRequest()
        assertNotNull(capturingHook.lastSuccess?.traceId?.ifBlank { null })
    }

    @Test
    fun `context durationMs is positive after request completes`() {
        doRequest()
        val ctx = capturingHook.lastSuccess!!
        assert(ctx.durationMs > 0) { "Expected positive durationMs but was ${ctx.durationMs}" }
    }
}
