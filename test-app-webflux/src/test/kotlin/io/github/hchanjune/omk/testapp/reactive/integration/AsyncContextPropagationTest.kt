package io.github.hchanjune.omk.testapp.reactive.integration

import io.github.hchanjune.omk.core.contants.ExecutionScope
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.testapp.reactive.CapturingHook
import io.github.hchanjune.omk.testapp.reactive.SampleAsyncEvent
import io.github.hchanjune.omk.testapp.reactive.SampleAsyncEventHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers what a coroutine that leaves the managed execution's thread records. Without
 * `ManagedContextElement` the aspects inside the new thread find no context and skip silently —
 * the call still happens, so the time shows up in the parent span with nothing under it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AsyncContextPropagationTest {

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var capturingHook: CapturingHook
    @Autowired private lateinit var eventHandler: SampleAsyncEventHandler

    private val client: WebTestClient by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @BeforeEach
    fun setUp() = capturingHook.clear()

    @Test
    fun `blocking event handler records the external call made on another dispatcher`() {
        eventHandler.handle(SampleAsyncEvent("42"))
        capturingHook.awaitSuccess()

        val forked = awaitForkedContext()
        assertNotNull(forked, "the async scope was never reported")
        assertTrue(
            allSpans(forked.rootSpan!!).any { it.descriptor.layer == MetricLayer.EXTERNAL },
            "expected an EXTERNAL span for the client call made inside withContext"
        )
    }

    @Test
    fun `the forked context continues the parent trace`() {
        eventHandler.handle(SampleAsyncEvent("42"))
        capturingHook.awaitSuccess()

        val forked = assertNotNull(awaitForkedContext())
        val parent = capturingHook.successes().first { it.executionScope == ExecutionScope.EVENT }

        assertEquals(parent.traceId, forked.traceId)
        assertEquals(parent.entrypoint, forked.entrypoint)
        assertEquals(ExecutionScope.ASYNC, forked.executionScope)
    }

    @Test
    fun `the forked context owns its span stack rather than the parent's`() {
        eventHandler.handle(SampleAsyncEvent("42"))
        capturingHook.awaitSuccess()

        val forked = assertNotNull(awaitForkedContext())
        val parent = capturingHook.successes().first { it.executionScope == ExecutionScope.EVENT }

        // The external span belongs to the fork; the parent's tree only has its own entry span.
        assertTrue(allSpans(parent.rootSpan!!).none { it.descriptor.layer == MetricLayer.EXTERNAL })
        assertTrue(allSpans(forked.rootSpan!!).all { it.durationMs != null }, "forked spans must be ended")
    }

    @Test
    fun `without the element the thread switch loses the context and records nothing`() {
        eventHandler.handleUnpropagated(SampleAsyncEvent("42"))
        capturingHook.awaitSuccess()

        val parent = capturingHook.successes().first { it.executionScope == ExecutionScope.EVENT }
        assertTrue(allSpans(parent.rootSpan!!).none { it.descriptor.layer == MetricLayer.EXTERNAL })
        assertNull(awaitForkedContext(timeoutMs = 300), "no async scope should be reported")
    }

    @Test
    fun `suspend request path records the external call made on another dispatcher`() {
        client.get().uri("/test/async-external").exchange().expectStatus().isOk()
        capturingHook.awaitSuccess()

        val forked = assertNotNull(awaitForkedContext(), "the async scope was never reported")
        assertTrue(
            allSpans(forked.rootSpan!!).any { it.descriptor.layer == MetricLayer.EXTERNAL },
            "expected an EXTERNAL span for the client call made inside withContext"
        )
    }

    // The forked scope and its parent complete independently, so the fork may be reported either
    // before or after the awaited success — poll briefly instead of assuming an order.
    private fun awaitForkedContext(timeoutMs: Long = 2000): ManagedContext? {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            capturingHook.successes().firstOrNull { it.executionScope == ExecutionScope.ASYNC }
                ?.let { return it }
            Thread.sleep(20)
        } while (System.currentTimeMillis() < deadline)
        return null
    }

    private fun allSpans(span: MetricSpan): List<MetricSpan> =
        listOf(span) + span.children.flatMap { allSpans(it) }
}
