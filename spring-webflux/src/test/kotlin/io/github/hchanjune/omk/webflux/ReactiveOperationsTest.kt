package io.github.hchanjune.omk.webflux

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.webflux.TestSupport.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ReactiveOperationsTest {

    private fun reactorCtxWith(ctx: ManagedContext): Context =
        Context.of(ReactiveOperations.CONTEXT_KEY, ctx)

    // 오버로드 모호성 해소 헬퍼
    private suspend fun <T> callSuspend(block: suspend ManagedContext.() -> T): OperationResult<T> =
        ReactiveOperations(block)

    private fun <T : Any> callMono(block: ManagedContext.() -> Mono<T>): Mono<OperationResult<T>> =
        ReactiveOperations.mono(block)

    // ── suspend invoke ────────────────────────────────────────────────────────

    @Test
    fun `suspend invoke returns data from block`() {
        val ctx = context()
        val result = mono(Dispatchers.Unconfined) { callSuspend { "hello" } }
            .contextWrite(reactorCtxWith(ctx))
            .block()

        assertNotNull(result)
        assertEquals("hello", result.data)
    }

    @Test
    fun `suspend invoke injects response into context`() {
        val ctx = context()
        mono(Dispatchers.Unconfined) { callSuspend { "response-value" } }
            .contextWrite(reactorCtxWith(ctx))
            .block()

        assertEquals("response-value", ctx.response)
    }

    @Test
    fun `suspend invoke provides ManagedContext as receiver`() {
        val ctx = context().also { it.injectTraceId("my-trace") }
        val result = mono(Dispatchers.Unconfined) { callSuspend { traceId } }
            .contextWrite(reactorCtxWith(ctx))
            .block()

        assertEquals("my-trace", result?.data)
    }

    @Test
    fun `suspend invoke throws when no ReactorContext present`() {
        assertFailsWith<IllegalStateException> {
            runBlocking { callSuspend { "fail" } }
        }
    }

    @Test
    fun `suspend invoke throws when ManagedContext not in ReactorContext`() {
        val mono = mono(Dispatchers.Unconfined) { callSuspend { "fail" } }
            .contextWrite(Context.of("other-key", "value"))

        StepVerifier.create(mono)
            .expectError(IllegalStateException::class.java)
            .verify()
    }

    // ── Mono invoke ───────────────────────────────────────────────────────────

    @Test
    fun `mono invoke returns data from block`() {
        val ctx = context()
        val result = callMono<String> { Mono.just("mono-result") }
            .contextWrite(reactorCtxWith(ctx))
            .block()

        assertNotNull(result)
        assertEquals("mono-result", result.data)
    }

    @Test
    fun `mono invoke injects response into context`() {
        val ctx = context()
        callMono<String> { Mono.just("mono-response") }
            .contextWrite(reactorCtxWith(ctx))
            .block()

        assertEquals("mono-response", ctx.response)
    }

    @Test
    fun `mono invoke fails when no ManagedContext`() {
        StepVerifier.create(
            callMono<String> { Mono.just("fail") }
                .contextWrite(Context.empty())
        )
            .expectError(IllegalStateException::class.java)
            .verify()
    }

    // ── configureHook ─────────────────────────────────────────────────────────

    @Test
    fun `configureHook sets hook instance`() {
        val hook = object : OperationHook {}
        ReactiveOperations.configureHook(hook)
        assertEquals(hook, ReactiveOperations.hook)
    }

    // ── initializeForEvent error path ────────────────────────────────────────

    @Test
    fun `initializeForEvent throws when providers not configured`() {
        val field = ReactiveOperations::class.java.getDeclaredField("eventContextProvider")
        field.isAccessible = true
        val previous = field.get(ReactiveOperations)
        field.set(ReactiveOperations, null)

        try {
            assertFailsWith<IllegalStateException> {
                ReactiveOperations.initializeForEvent(EventMetadata())
            }
        } finally {
            field.set(ReactiveOperations, previous)
        }
    }
}
