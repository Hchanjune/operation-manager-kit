package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.OperationExecutor

object DefaultOperationExecutorFactory {
    fun create(): OperationExecutor =
        OperationExecutor(
            invocationInfoProvider = DefaultInvocationInfoProvider,
            issuerProvider = DefaultIssuerProvider,
            correlationIdProvider = DefaultCorrelationIdProvider,
            hooks = NoopOperationHooks
        )
}