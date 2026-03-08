package io.github.hchanjune.omk.core.providers.operation

import io.github.hchanjune.omk.core.models.context.OperationContext
import io.github.hchanjune.omk.core.models.invocation.InvocationInfo

fun interface OperationContextProvider {
    fun current(issuer: String, invocation: InvocationInfo): OperationContext
}