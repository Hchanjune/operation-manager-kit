package io.github.hchanjune.omk.core.providers.operation

import io.github.hchanjune.omk.core.OperationContextHolder

fun interface OperationContextHolderProvider {
    fun current(): OperationContextHolder
}