package io.github.hchanjune.operationresult.core.providers.operation

import io.github.hchanjune.operationresult.core.models.invocation.InvocationInfo
import io.github.hchanjune.operationresult.core.models.context.OperationContext

fun interface OperationContextProvider {
    fun current(issuer: String, invocation: InvocationInfo): OperationContext
}