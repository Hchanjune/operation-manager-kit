package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.context.ManagedContext

class OperationExecutor {

    fun <T> run(
        context: ManagedContext,
        block: ManagedContext.() -> T
    ): OperationResult<T> {
        return try {
            val result = context.block()
            OperationResult(
                context = context,
                data = result
            )
        } catch (exception: Throwable) {

            throw exception
        }
    }

}