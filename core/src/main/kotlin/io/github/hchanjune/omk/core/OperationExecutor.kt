package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.context.ManagedContext

class OperationExecutor {

    fun <T> run(context: ManagedContext, block: ManagedContext.() -> T): OperationResult<T> {
        val data = context.block()
        context.injectResponse(data?.toString() ?: "null")
        return OperationResult(context = context, data = data)
    }

}