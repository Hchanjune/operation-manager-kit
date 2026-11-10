package io.github.hchanjune.omk.testapp.servlet.integration

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * The matched route is only known after the OMK filter has passed the request on. This checks, on a
 * real DispatcherServlet, that it is there by the time the controller span opens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(HttpRouteSpanTest.Tracing::class)
class HttpRouteSpanTest {

    @TestConfiguration
    class Tracing {
        @Bean
        fun spanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()

        @Bean(destroyMethod = "close")
        fun sdkTracerProvider(exporter: InMemorySpanExporter): SdkTracerProvider =
            SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()

        @Bean
        fun tracer(provider: SdkTracerProvider): Tracer = provider.get("test")
    }

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var exporter: InMemorySpanExporter

    @BeforeEach
    fun setUp() = exporter.reset()

    private fun get(path: String) {
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.discarding(),
        )
    }

    private fun awaitSpan(name: String): SpanData {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            exporter.finishedSpanItems.firstOrNull { it.name == name }?.let { return it }
            Thread.sleep(20)
        }
        error("span [$name] not exported; got ${exporter.finishedSpanItems.map { it.name }}")
    }

    private fun SpanData.attribute(key: String): String? =
        attributes.asMap().entries.firstOrNull { it.key.key == key }?.value?.toString()

    @Test
    fun `controller span carries the route template, not the resolved path`() {
        get("/sample/items/1")

        val span = awaitSpan("ItemController.getById")
        assertEquals("/sample/items/{id}", span.attribute("http.route"))
        assertEquals("GET", span.attribute("http.request.method"))
    }

    @Test
    fun `route without variables is reported as is`() {
        get("/sample/items")

        assertEquals("/sample/items", awaitSpan("ItemController.getAll").attribute("http.route"))
    }
}
