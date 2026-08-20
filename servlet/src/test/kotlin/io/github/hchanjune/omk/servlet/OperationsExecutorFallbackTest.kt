package io.github.hchanjune.omk.servlet

import io.github.hchanjune.omk.core.OperationRuntime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The default runtime is global state that other test classes configure, so this class
 * resets it around every test — the fallback only engages while no executor is configured.
 */
class OperationsExecutorFallbackTest {

    @BeforeTest
    fun setUp() {
        Operations.clear()
        Operations.configureDefaultRuntime(OperationRuntime())
    }

    @AfterTest
    fun tearDown() {
        Operations.clear()
        Operations.configureDefaultRuntime(OperationRuntime())
    }

    @Test
    fun `invoke runs the block when no executor is configured`() {
        val result = Operations { "fallback-data" }

        assertEquals("fallback-data", result.data)
        assertTrue(!Operations.hasContext)
    }

    @Test
    fun `fallback execution still records into the managed context`() {
        val result = Operations {
            message = "fell back but still managed"
            42
        }

        assertEquals(42, result.data)
        assertEquals("fell back but still managed", result.context.message)
        assertEquals("42", result.context.response)
    }

    @Test
    fun `fallback is reused across invocations`() {
        assertEquals("first", Operations { "first" }.data)
        assertEquals("second", Operations { "second" }.data)
    }
}
