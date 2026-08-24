package io.github.hchanjune.omk.reactive.async

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.contants.ExecutionScope
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.reactive.ReactiveOperations
import io.github.hchanjune.omk.reactive.aspect.ReactiveAspectSupport
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import reactor.util.context.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class ManagedContextElementTest {

    private val spanIdProvider = SpanIdProvider { "elem-test-span" }

    private fun makeContext(asyncHookEnabled: Boolean = false): ManagedContext =
        ManagedContext(spanIdProvider = spanIdProvider).apply {
            injectTraceId("trace-elem")
            if (asyncHookEnabled) enableAsyncHook()
        }

    private fun captureHook(
        latch: CountDownLatch,
        onSuccess: (ManagedContext) -> Unit = {},
        onFailure: (ManagedContext, Throwable) -> Unit = { _, _ -> }
    ) = object : OperationHook {
        override fun onSuccess(context: ManagedContext) { onSuccess(context); latch.countDown() }
        override fun onFailure(context: ManagedContext, exception: Throwable) { onFailure(context, exception); latch.countDown() }
    }

    @BeforeTest
    fun setUp() = ReactiveAspectSupport.threadBoundContext.remove()

    @AfterTest
    fun tearDown() = ReactiveAspectSupport.threadBoundContext.remove()

    @Test
    fun `copyForChild forks the context instead of sharing it`() {
        val parent = makeContext()
        val child = (ManagedContextElement(parent).copyForChild() as ManagedContextElement).managedContext

        assertNotSame(parent, child)
        assertEquals(parent.traceId, child.traceId)
        assertEquals(ExecutionScope.ASYNC, child.executionScope)
    }

    @Test
    fun `mergeForChild returns the overwriting element`() {
        val element = ManagedContextElement(makeContext())
        val overwriting = ManagedContextElement(makeContext())

        assertSame(overwriting, element.mergeForChild(overwriting))
    }

    @Test
    fun `updateThreadContext binds the context and returns the previous one`() {
        val previous = makeContext()
        val current = makeContext()
        ReactiveAspectSupport.threadBoundContext.set(previous)

        val old = runBlocking { ManagedContextElement(current).updateThreadContext(coroutineContext) }

        assertSame(previous, old)
        assertSame(current, ReactiveAspectSupport.threadBoundContext.get())
    }

    @Test
    fun `restoreThreadContext clears the binding when there was no previous context`() {
        val context = makeContext()
        ReactiveAspectSupport.threadBoundContext.set(context)

        runBlocking { ManagedContextElement(context).restoreThreadContext(coroutineContext, null) }

        assertNull(ReactiveAspectSupport.threadBoundContext.get())
    }

    @Test
    fun `restoreThreadContext reinstates the previous context`() {
        val previous = makeContext()
        val current = makeContext()
        ReactiveAspectSupport.threadBoundContext.set(current)

        runBlocking { ManagedContextElement(current).restoreThreadContext(coroutineContext, previous) }

        assertSame(previous, ReactiveAspectSupport.threadBoundContext.get())
    }

    @Test
    fun `withContext binds the forked context on the new thread`() = runBlocking {
        val parent = makeContext()

        val bound = withContext(Dispatchers.IO + ManagedContextElement(parent)) {
            ReactiveAspectSupport.threadBoundContext.get()
        }

        assertNotSame(parent, bound)
        assertEquals(parent.traceId, bound?.traceId)
        // the coroutine's own binding must not leak onto the caller's thread
        assertNull(ReactiveAspectSupport.threadBoundContext.get())
    }

    @Test
    fun `currentContextOrNull prefers the element over the inherited Reactor context`() = runBlocking {
        val fromPipeline = makeContext()
        val opened = makeContext()
        val reactorContext = ReactorContext(Context.of(ReactiveOperations.CONTEXT_KEY, fromPipeline))

        val resolved = withContext(reactorContext) {
            withContext(ManagedContextElement(opened)) { ReactiveOperations.currentContextOrNull() }
        }

        // the element's forked context wins — a fork of `opened`, never `fromPipeline`
        assertEquals(ExecutionScope.ASYNC, resolved?.executionScope)
        assertNotSame(fromPipeline, resolved)
    }

    @Test
    fun `managedContextElement resolves the thread-bound context`() = runBlocking {
        val entryPointContext = makeContext()
        ReactiveAspectSupport.threadBoundContext.set(entryPointContext)

        val element = managedContextElement()

        assertTrue(element is ManagedContextElement)
        assertSame(entryPointContext, element.managedContext)
    }

    @Test
    fun `managedContextElement returns empty when nothing is managed`() = runBlocking {
        assertSame(EmptyCoroutineContext, managedContextElement())
    }

    @Test
    fun `async hook reports the forked context on completion`() {
        val latch = CountDownLatch(1)
        var reported: ManagedContext? = null
        ReactiveOperations.configureHook(captureHook(latch, onSuccess = { reported = it }))

        GlobalScope.launch(ManagedContextElement(makeContext(asyncHookEnabled = true))) { }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "hook was not called")
        assertEquals(ExecutionScope.ASYNC, reported?.executionScope)
        assertEquals("trace-elem", reported?.traceId)
        assertNotNull(reported?.rootSpan?.durationMs, "root span was not ended")
    }

    @Test
    fun `async hook reports failure when the coroutine throws`() {
        val latch = CountDownLatch(1)
        var reported: Throwable? = null
        ReactiveOperations.configureHook(captureHook(latch, onFailure = { _, e -> reported = e }))

        GlobalScope.launch(ManagedContextElement(makeContext(asyncHookEnabled = true))) {
            throw RuntimeException("coroutine-failure")
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "hook was not called")
        assertEquals("coroutine-failure", reported?.message)
    }

    @Test
    fun `hook is registered once even when the coroutine resumes repeatedly`() {
        var calls = 0
        val latch = CountDownLatch(1)
        ReactiveOperations.configureHook(captureHook(latch, onSuccess = { calls++ }))

        val element = ManagedContextElement(makeContext(asyncHookEnabled = true).forkAsync())
        runBlocking {
            element.updateThreadContext(coroutineContext)
            element.updateThreadContext(coroutineContext)
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "hook was not called")
        assertEquals(1, calls)
    }

    @Test
    fun `no hook fires when the async hook is disabled`() {
        var called = false
        ReactiveOperations.configureHook(
            captureHook(CountDownLatch(1), onSuccess = { called = true }, onFailure = { _, _ -> called = true })
        )

        runBlocking { GlobalScope.launch(ManagedContextElement(makeContext(asyncHookEnabled = false))) { }.join() }

        assertTrue(!called)
    }
}
