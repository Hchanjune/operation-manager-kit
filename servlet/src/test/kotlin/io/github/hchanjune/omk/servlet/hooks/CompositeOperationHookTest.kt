package io.github.hchanjune.omk.servlet.hooks

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.servlet.TestSupport.context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeOperationHookTest {

    private fun hook(onSuccess: (ManagedContext) -> Unit = {}) = object : OperationHook {
        override fun onSuccess(context: ManagedContext) = onSuccess(context)
    }

    // ── Ordering ──────────────────────────────────────────────────────────

    @Test
    fun `hooks are invoked in list order on onSuccess`() {
        val order = mutableListOf<Int>()
        val composite = CompositeOperationHook(listOf(
            hook { order.add(1) },
            hook { order.add(2) },
            hook { order.add(3) }
        ))
        composite.onSuccess(context())
        assertEquals(listOf(1, 2, 3), order)
    }

    @Test
    fun `hooks are invoked in list order on onFailure`() {
        val order = mutableListOf<Int>()
        val composite = CompositeOperationHook(listOf(
            object : OperationHook { override fun onFailure(context: ManagedContext, exception: Throwable) { order.add(1) } },
            object : OperationHook { override fun onFailure(context: ManagedContext, exception: Throwable) { order.add(2) } }
        ))
        composite.onFailure(context(), RuntimeException())
        assertEquals(listOf(1, 2), order)
    }

    // ── Failure isolation ─────────────────────────────────────────────────

    @Test
    fun `a throwing hook does not prevent subsequent hooks from running`() {
        var secondCalled = false
        val composite = CompositeOperationHook(listOf(
            hook { throw RuntimeException("boom") },
            hook { secondCalled = true }
        ))
        composite.onSuccess(context()) // must not propagate the exception
        assertTrue(secondCalled)
    }

    @Test
    fun `failing hook is recorded in context hookRecords`() {
        val ctx = context()
        val composite = CompositeOperationHook(listOf(
            hook { throw RuntimeException("hook-error") },
            hook { }
        ))
        composite.onSuccess(ctx)
        assertEquals(2, ctx.hookRecords.size)
        assertFalse(ctx.hookRecords[0].success)
        assertTrue(ctx.hookRecords[1].success)
    }

    // ── Idempotency ───────────────────────────────────────────────────────

    @Test
    fun `onSuccess is idempotent - second call is skipped`() {
        var callCount = 0
        val composite = CompositeOperationHook(listOf(hook { callCount++ }))
        val ctx = context()
        composite.onSuccess(ctx)
        composite.onSuccess(ctx)
        assertEquals(1, callCount)
    }

    @Test
    fun `onFailure is idempotent - second call is skipped`() {
        var callCount = 0
        val composite = CompositeOperationHook(listOf(
            object : OperationHook { override fun onFailure(context: ManagedContext, exception: Throwable) { callCount++ } }
        ))
        val ctx = context()
        composite.onFailure(ctx, RuntimeException())
        composite.onFailure(ctx, RuntimeException())
        assertEquals(1, callCount)
    }

    @Test
    fun `onSuccess after onFailure is skipped`() {
        var successCount = 0
        val composite = CompositeOperationHook(listOf(hook { successCount++ }))
        val ctx = context()
        composite.onFailure(ctx, RuntimeException())
        composite.onSuccess(ctx)
        assertEquals(0, successCount)
    }

    // ── Empty list ────────────────────────────────────────────────────────

    @Test
    fun `empty hook list does not throw`() {
        val composite = CompositeOperationHook(emptyList())
        composite.onSuccess(context())
        composite.onFailure(context(), RuntimeException())
    }
}
