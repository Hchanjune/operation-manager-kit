package io.github.hchanjune.omk.testapp.servlet.integration

import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.testapp.servlet.CapturingHook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AopSpanTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook

    private val restTemplate = RestTemplate()
    private fun url(path: String) = "http://localhost:$port$path"

    @BeforeEach
    fun setUp() = capturingHook.clear()

    @Test
    fun `successful request produces a root span`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()
        assertNotNull(capturingHook.lastSuccess?.rootSpan)
    }

    @Test
    fun `ManagedController creates an ENTRY layer span`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val spans = allSpans(capturingHook.lastSuccess?.rootSpan!!)
        assertTrue(spans.any { it.descriptor.layer == MetricLayer.ENTRY },
            "Expected at least one ENTRY span")
    }

    @Test
    fun `ManagedOperation creates an APPLICATION layer span`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val spans = allSpans(capturingHook.lastSuccess?.rootSpan!!)
        assertTrue(spans.any { it.descriptor.layer == MetricLayer.APPLICATION },
            "Expected at least one APPLICATION span")
    }

    @Test
    fun `controller span name contains class and method name`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val spans = allSpans(capturingHook.lastSuccess?.rootSpan!!)
        assertTrue(spans.any { it.name.value.contains("SampleController") },
            "Expected a span named after SampleController")
    }

    @Test
    fun `operation span name matches ManagedOperation annotation value`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val spans = allSpans(capturingHook.lastSuccess?.rootSpan!!)
        assertTrue(spans.any { it.name.value == "ProcessSample" },
            "Expected span named 'ProcessSample' from @ManagedOperation")
    }

    @Test
    fun `ENTRY span is root and APPLICATION span is its child`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val root = capturingHook.lastSuccess?.rootSpan!!
        assertEquals(MetricLayer.ENTRY, root.descriptor.layer)
        assertTrue(root.children.any { it.descriptor.layer == MetricLayer.APPLICATION },
            "ENTRY span should have APPLICATION child")
    }

    @Test
    fun `all spans in the tree are ended`() {
        restTemplate.getForEntity(url("/test/ok"), String::class.java)
        capturingHook.awaitSuccess()

        val spans = allSpans(capturingHook.lastSuccess?.rootSpan!!)
        assertTrue(spans.all { it.durationMs != null },
            "Every span should have a duration (i.e. be ended)")
    }

    @Test
    fun `span traceId matches the propagated W3C traceId`() {
        val headers = HttpHeaders().apply {
            set("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        }
        restTemplate.exchange(url("/test/ok"), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        capturingHook.awaitSuccess()

        val ctx = capturingHook.lastSuccess!!
        val spans = allSpans(ctx.rootSpan!!)
        assertTrue(spans.all { it.traceId == "4bf92f3577b34da6a3ce929d0e0e4736" },
            "All spans should carry the propagated traceId")
    }

    private fun allSpans(root: MetricSpan): List<MetricSpan> = buildList {
        fun dfs(span: MetricSpan) { add(span); span.children.forEach { dfs(it) } }
        dfs(root)
    }
}
