package io.github.hchanjune.operationresult.core.providers.operation

import io.github.hchanjune.operationresult.core.OperationContextHolder

fun interface OperationContextHolderProvider {
    fun current(): OperationContextHolder
}