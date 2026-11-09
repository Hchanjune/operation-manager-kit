package io.github.hchanjune.omk.reactive

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.TestSupport.context
import io.github.hchanjune.omk.reactive.aspect.ReactiveAspectSupport
import io.github.hchanjune.omk.reactive.async.ManagedContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `suspend invoke proceeds unmanaged when no ReactorContext present`() {
        val result = runBlocking { callSuspend { "unmanaged-ok" } }
        assertEquals("unmanaged-ok", result.data)
    }

    @Test
    fun `suspend invoke proceeds unmanaged when ManagedContext not in ReactorContext`() {
        val mono = mono(Dispatchers.Unconfined) { callSuspend { "unmanaged-ok" } }
            .contextWrite(Context.of("other-key", "value"))

        StepVerifier.create(mono)
            .expectNextMatches { it.data == "unmanaged-ok" }
            .verifyComplete()
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
    fun `mono invoke proceeds unmanaged when no ManagedContext`() {
        StepVerifier.create(
            callMono<String> { Mono.just("unmanaged-ok") }
                .contextWrite(Context.empty())
        )
            .expectNextMatches { it.data == "unmanaged-ok" }
            .verifyComplete()
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
        val field = ReactiveOperations::class.java.getDeclaredField("defaultRuntime")
        field.isAccessible = true
        val previous = field.get(ReactiveOperations)
        field.set(ReactiveOperations, OperationRuntime())

        try {
            assertFailsWith<IllegalStateException> {
                ReactiveOperations.initializeForEvent(EventMetadata())
            }
        } finally {
            field.set(ReactiveOperations, previous)
        }
    }

    // ── runtime isolation ─────────────────────────────────────────────────────

    @Test
    fun `hookFor prefers context-attached runtime hook over default`() {
        val defaultHook = object : OperationHook {}
        val runtimeHook = object : OperationHook {}
        ReactiveOperations.configureHook(defaultHook)

        val ctx = context().apply { runtime = OperationRuntime().apply { hook = runtimeHook } }
        assertEquals(runtimeHook, ReactiveOperations.hookFor(ctx))

        val plainCtx = context()
        assertEquals(defaultHook, ReactiveOperations.hookFor(plainCtx))
    }

    @Test
    fun `initializeForEvent with explicit runtime uses its providers and attaches it`() {
        val rt = OperationRuntime().apply {
            contextProvider = object : ManagedContextProvider { override fun provide() = context() }
            traceIdProvider = object : TraceIdProvider { override fun provideTraceId() = "rt-trace" }
            causationIdProvider = object : CausationIdProvider { override fun provideCausationId() = "rt-cause" }
        }
        val ctx = ReactiveOperations.initializeForEvent(EventMetadata(), rt)
        assertEquals("rt-trace", ctx.traceId)
        assertEquals("rt-cause", ctx.causationId)
        assertEquals(rt, ctx.runtime)
    }

    // ── threadBoundContextOrNull ──────────────────────────────────────────────

    @Test
    fun `threadBoundContextOrNull is null outside any managed scope`() {
        ReactiveAspectSupport.threadBoundContext.remove()
        assertNull(ReactiveOperations.threadBoundContextOrNull())
    }

    @Test
    fun `threadBoundContextOrNull sees the forked context of a ManagedContextElement on the worker thread`() {
        ReactiveAspectSupport.threadBoundContext.remove()
        val ctx = context().also { it.injectTraceId("parent-trace") }
        val seen = runBlocking {
            withContext(Dispatchers.IO + ManagedContextElement(ctx)) {
                // Plain blocking call, as an HTTP client interceptor would make
                ReactiveOperations.threadBoundContextOrNull()
            }
        }
        // The element forks per coroutine — same trace, its own span stack (async.execution root)
        assertNotNull(seen)
        assertEquals("parent-trace", seen.traceId)
        assertTrue(seen.isAsync)
        assertEquals("async.execution", seen.peek()?.name?.value)
        assertNull(ReactiveOperations.threadBoundContextOrNull())
    }

    @Test
    fun `threadBoundContextOrNull sees the context opened by a blocking entry point`() {
        val ctx = context()
        ReactiveAspectSupport.threadBoundContext.set(ctx)
        try {
            assertSame(ctx, ReactiveOperations.threadBoundContextOrNull())
        } finally {
            ReactiveAspectSupport.threadBoundContext.remove()
        }
    }

    @Test
    fun `threadBoundContextOrNull ignores a context carried only in the Reactor context`() {
        ReactiveAspectSupport.threadBoundContext.remove()
        val ctx = context()
        val seen = mono(Dispatchers.Unconfined) { ReactiveOperations.threadBoundContextOrNull() ?: "none" }
            .contextWrite(reactorCtxWith(ctx))
            .block()
        assertEquals("none", seen)
    }
}
