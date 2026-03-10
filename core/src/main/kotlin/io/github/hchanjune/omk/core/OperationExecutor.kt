package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.context.ManagedContextHolder
import io.github.hchanjune.omk.core.context.ManagedContext


class OperationExecutor(
    val contextHolder: ManagedContextHolder
) {

    fun <T> run(block: ManagedContext.() -> T): OperationResult<T> {
        val context = contextHolder.context?: throw IllegalStateException("ManagedContext Not Found")
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