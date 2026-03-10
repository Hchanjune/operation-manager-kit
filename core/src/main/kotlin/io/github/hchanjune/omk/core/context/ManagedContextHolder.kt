package io.github.hchanjune.omk.core.context

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationResult

interface ManagedContextHolder {
    val context: ManagedContext
    val hasContext: Boolean
    fun initialize(context: ManagedContext)
    fun configure(executor: OperationExecutor)
    operator fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T>
    fun applyContext(context: ManagedContext)
    fun clear()
}