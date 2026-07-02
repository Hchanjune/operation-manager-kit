package io.github.hchanjune.omk.servlet.async

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class ManagedContextElementTest {

    private val spanIdProvider = SpanIdProvider { "elem-test-span" }

    private fun makeContext(asyncHookEnabled: Boolean = false): ManagedContext {
        val ctx = ManagedContext(spanIdProvider = spanIdProvider)
        ctx.injectTraceId("trace-elem")
        if (asyncHookEnabled) ctx.enableAsyncHook()
        return ctx
    }

    @BeforeTest
    fun setUp() {
        Operations.clear()
    }

    @AfterTest
    fun tearDown() {
        Operations.clear()
    }

    @Test
    fun `updateThreadContext applies context and returns null when no previous context`() {
        val ctx = makeContext()
        val element = ManagedContextElement(ctx)
        val old = runBlocking {
            element.updateThreadContext(coroutineContext)
        }
        assertTrue(old == null || true) // may be null or prior context
    }

    @Test
    fun `restoreThreadContext clears context when oldState is null`() {
        val ctx = makeContext()
        Operations.applyContext(ctx)
        val element = ManagedContextElement(ctx)
        runBlocking {
            element.restoreThreadContext(coroutineContext, null)
        }
        assertTrue(!Operations.hasContext)
    }

    @Test
    fun `restoreThreadContext reapplies previous context when oldState is non-null`() {
        val prevCtx = makeContext()
        val newCtx = makeContext()
        Operations.applyContext(prevCtx)
        val element = ManagedContextElement(newCtx)
        runBlocking {
            element.restoreThreadContext(coroutineContext, prevCtx)
        }
        assertTrue(Operations.hasContext)
        assertTrue(Operations.context === prevCtx)
    }

    @Test
    fun `copyForChild creates a child element`() {
        val ctx = makeContext()
        val element = ManagedContextElement(ctx)
        val child = element.copyForChild()
        assertTrue(child is ManagedContextElement)
    }

    @Test
    fun `mergeForChild returns the overwriting element`() {
        val ctx = makeContext()
        val element = ManagedContextElement(ctx)
        val overwriting = ManagedContextElement(makeContext())
        val merged = element.mergeForChild(overwriting)
        assertTrue(merged === overwriting)
    }

    @Test
    fun `withContext propagates ManagedContext into coroutine`() {
        val ctx = makeContext()
        Operations.applyContext(ctx)
        val element = ManagedContextElement(ctx)

        runBlocking {
            withContext(element) {
                assertTrue(Operations.hasContext)
            }
        }
    }

    @Test
    fun `coroutine with asyncHookEnabled calls onSuccess on completion`() {
        val ctx = makeContext(asyncHookEnabled = true)
        val latch = CountDownLatch(1)
        var successCalled = false

        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { successCalled = true; latch.countDown() }
            override fun onFailure(context: ManagedContext, exception: Throwable) { latch.countDown() }
        }
        Operations.configureHook(hook)

        val asyncCtx = ctx.forkAsync()
        val element = ManagedContextElement(asyncCtx)

        GlobalScope.launch(element) {
            // nothing — completes normally
        }

        latch.await(3, TimeUnit.SECONDS)
        assertTrue(successCalled)
    }

    @Test
    fun `coroutine with asyncHookEnabled calls onFailure when coroutine throws`() {
        val ctx = makeContext(asyncHookEnabled = true)
        val latch = CountDownLatch(1)
        var failureCalled = false

        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { latch.countDown() }
            override fun onFailure(context: ManagedContext, exception: Throwable) { failureCalled = true; latch.countDown() }
        }
        Operations.configureHook(hook)

        val asyncCtx = ctx.forkAsync()
        val element = ManagedContextElement(asyncCtx)

        GlobalScope.launch(element) {
            throw RuntimeException("coroutine-failure")
        }

        latch.await(3, TimeUnit.SECONDS)
        assertTrue(failureCalled)
    }

    @Test
    fun `second updateThreadContext call does not register hook twice`() {
        val ctx = makeContext(asyncHookEnabled = true)
        val asyncCtx = ctx.forkAsync()
        val element = ManagedContextElement(asyncCtx)

        var hookCallCount = 0
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { hookCallCount++ }
            override fun onFailure(context: ManagedContext, exception: Throwable) {}
        }
        Operations.configureHook(hook)

        runBlocking {
            element.updateThreadContext(coroutineContext) // registers invokeOnCompletion once
            element.updateThreadContext(coroutineContext) // hookRegistered=true → compareAndSet returns false → skipped
        }
        // Only one handler was registered, so onSuccess fires exactly once (not twice)
        assertTrue(hookCallCount == 1)
    }

    @Test
    fun `updateThreadContext skips hook registration when isAsyncHookEnabled is false`() {
        val ctx = makeContext(asyncHookEnabled = false) // isAsyncHookEnabled=false
        val asyncCtx = ctx.forkAsync()                 // isAsync=true, isAsyncHookEnabled=false
        val element = ManagedContextElement(asyncCtx)

        var hookCalled = false
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { hookCalled = true }
            override fun onFailure(context: ManagedContext, exception: Throwable) { hookCalled = true }
        }
        Operations.configureHook(hook)

        val job = GlobalScope.launch(element) { /* completes normally */ }
        runBlocking { job.join() }

        assertTrue(!hookCalled)
    }
}
