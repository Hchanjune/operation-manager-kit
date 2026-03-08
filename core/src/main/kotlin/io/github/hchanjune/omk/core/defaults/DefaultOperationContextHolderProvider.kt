package io.github.hchanjune.omk.core.defaults

import io.github.hchanjune.omk.core.OperationContextHolder
import io.github.hchanjune.omk.core.providers.operation.OperationContextHolderProvider

object DefaultOperationContextHolderProvider: OperationContextHolderProvider {
    override fun current(): OperationContextHolder {
        return DefaultOperationContextHolder()
    }
}