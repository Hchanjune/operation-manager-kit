package io.github.hchanjune.omk.webmvc

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.context.ManagedContextHolder

object Operations: ManagedContextHolder {

    private val contextHolder = ThreadLocal<ManagedContext>()

    private lateinit var executor: OperationExecutor

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
        context.start()
    }

    override fun complete() {
        contextHolder.get()?.end()
    }

    override fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T> {
        val exe = executor
        return exe.run(context, block)
    }

    override fun configure(executor: OperationExecutor)  {
        this.executor = executor
    }

    override fun clear() {
        contextHolder.remove()
    }

}