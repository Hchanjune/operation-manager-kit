package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.invocation.InvocationInfo
import io.github.hchanjune.operationresult.core.models.context.OperationContext
import io.github.hchanjune.operationresult.core.providers.operation.OperationContextProvider

object DefaultOperationContextProvider: OperationContextProvider {
    override fun current(
        issuer: String,
        invocation: InvocationInfo
    ) = OperationContext(
            issuer = issuer,
            entrypoint = invocation.entrypoint,
            service = invocation.service,
            function = invocation.function,
            operation = invocation.operation,
            useCase = invocation.useCase,
            event = invocation.event,
            attributes = invocation.attributes,
    )
}