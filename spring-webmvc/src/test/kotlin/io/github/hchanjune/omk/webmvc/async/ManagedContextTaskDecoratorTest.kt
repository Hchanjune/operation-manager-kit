package io.github.hchanjune.omk.webmvc.async

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webmvc.Operations
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ManagedContextTaskDecoratorTest {

    private val spanIdProvider = SpanIdProvider { "task-test-span" }

    private fun makeContext(): ManagedContext = ManagedContext(spanIdProvider = spanIdProvider).also { it.start() }

    @BeforeTest
    fun setUp() {
        Operations.clear()
    }

    @AfterTest
    fun tearDown() {
        Operations.clear()
    }

    @Test
    fun `returns original runnable when no context is set`() {
        val original = Runnable {}
        val decorated = ManagedContextTaskDecorator().decorate(original)
        assertSame(original, decorated)
    }

    @Test
    fun `decorated runnable executes when asyncHookEnabled is false`() {
        val ctx = makeContext()
        Operations.applyContext(ctx)

        var ran = false
        val decorated = ManagedContextTaskDecorator().decorate(Runnable { ran = true })
        Operations.clear()
        decorated.run()

        assertTrue(ran)
    }

    @Test
    fun `calls onSuccess when asyncHookEnabled is true and runnable completes normally`() {
        val ctx = makeContext()
        ctx.enableAsyncHook()
        Operations.applyContext(ctx)

        var successCalled = false
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { successCalled = true }
            override fun onFailure(context: ManagedContext, exception: Throwable) {}
        }
        Operations.configureHook(hook)

        val decorated = ManagedContextTaskDecorator().decorate(Runnable { })
        Operations.clear()
        decorated.run()

        assertTrue(successCalled)
    }

    @Test
    fun `calls onFailure and rethrows when asyncHookEnabled is true and runnable throws`() {
        val ctx = makeContext()
        ctx.enableAsyncHook()
        Operations.applyContext(ctx)

        var failureCalled = false
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) {}
            override fun onFailure(context: ManagedContext, exception: Throwable) { failureCalled = true }
        }
        Operations.configureHook(hook)

        val decorated = ManagedContextTaskDecorator().decorate(Runnable { throw RuntimeException("boom") })
        Operations.clear()

        assertFailsWith<RuntimeException>("boom") { decorated.run() }
        assertTrue(failureCalled)
    }

    @Test
    fun `does not call hook when asyncHookEnabled is false even if hook is configured`() {
        val ctx = makeContext()
        Operations.applyContext(ctx)

        var hookCalled = false
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { hookCalled = true }
            override fun onFailure(context: ManagedContext, exception: Throwable) { hookCalled = true }
        }
        Operations.configureHook(hook)

        val decorated = ManagedContextTaskDecorator().decorate(Runnable { })
        Operations.clear()
        decorated.run()

        assertTrue(!hookCalled)
    }

    @Test
    fun `rethrows exception without calling hook when asyncHookEnabled is false`() {
        val ctx = makeContext()
        Operations.applyContext(ctx) // asyncHookEnabled=false by default

        var hookCalled = false
        val hook = object : OperationHook {
            override fun onSuccess(context: ManagedContext) { hookCalled = true }
            override fun onFailure(context: ManagedContext, exception: Throwable) { hookCalled = true }
        }
        Operations.configureHook(hook)

        val decorated = ManagedContextTaskDecorator().decorate(Runnable { throw RuntimeException("rethrow-no-hook") })
        Operations.clear()

        assertFailsWith<RuntimeException> { decorated.run() }
        assertTrue(!hookCalled)
    }
}
