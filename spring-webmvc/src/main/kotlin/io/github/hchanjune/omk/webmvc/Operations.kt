package io.github.hchanjune.omk.webmvc

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.context.ManagedContextHolder

object Operations: ManagedContextHolder {

    private val contextHolder = ThreadLocal<ManagedContext>()

    @Volatile
    private var executor: OperationExecutor? = null

    override val context: ManagedContext
        get() = contextHolder.get()

    override val hasContext: Boolean
        get() = contextHolder.get() != null

    override fun applyContext(context: ManagedContext) {
        contextHolder.remove()
        contextHolder.set(context)
    }

    override fun initialize(context: ManagedContext) {
        contextHolder.set(context)
    }

    override fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T> {
        val exe = executor?: throw IllegalStateException("Executor not initialized")
        return exe.run(block)
    }

    override fun configure(executor: OperationExecutor)  {
        this.executor = executor
    }

    override fun clear() {
        contextHolder.remove()
    }

}