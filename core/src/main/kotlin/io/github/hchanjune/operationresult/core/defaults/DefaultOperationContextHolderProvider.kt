package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.OperationContextHolder
import io.github.hchanjune.operationresult.core.providers.operation.OperationContextHolderProvider

object DefaultOperationContextHolderProvider: OperationContextHolderProvider {
    override fun current(): OperationContextHolder {
        return DefaultOperationContextHolder()
    }
}