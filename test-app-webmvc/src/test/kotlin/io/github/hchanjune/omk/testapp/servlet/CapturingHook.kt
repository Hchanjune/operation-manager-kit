package io.github.hchanjune.omk.testapp.servlet

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@Component
@Order(1)
class CapturingHook : OperationHook {

    private val successContexts   = CopyOnWriteArrayList<ManagedContext>()
    private val failureContexts   = CopyOnWriteArrayList<ManagedContext>()
    private val failureExceptions = CopyOnWriteArrayList<Throwable>()

    @Volatile private var successSemaphore = Semaphore(0)
    @Volatile private var failureSemaphore = Semaphore(0)

    val lastSuccess: ManagedContext?  get() = successContexts.lastOrNull()
    val lastFailure: ManagedContext?  get() = failureContexts.lastOrNull()
    val lastException: Throwable?     get() = failureExceptions.lastOrNull()
    val successCount: Int             get() = successContexts.size
    val failureCount: Int             get() = failureContexts.size

    fun clear() {
        successContexts.clear()
        failureContexts.clear()
        failureExceptions.clear()
        successSemaphore.drainPermits()
        failureSemaphore.drainPermits()
    }

    fun awaitSuccess(timeoutMs: Long = 3000) {
        check(successSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            "onSuccess was not captured within ${timeoutMs}ms"
        }
    }

    fun awaitFailure(timeoutMs: Long = 3000) {
        check(failureSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            "onFailure was not captured within ${timeoutMs}ms"
        }
    }

    override fun onSuccess(context: ManagedContext) {
        successContexts.add(context)
        successSemaphore.release()
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        failureContexts.add(context)
        failureExceptions.add(exception)
        failureSemaphore.release()
    }
}
