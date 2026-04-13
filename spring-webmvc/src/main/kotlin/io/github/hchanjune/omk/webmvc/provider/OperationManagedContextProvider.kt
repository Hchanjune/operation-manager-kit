package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.IssuerProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider

class OperationManagedContextProvider(
    private val traceIdProvider: TraceIdProvider,
    private val causationIdProvider: CausationIdProvider,
    private val issuerProvider: IssuerProvider,
    private val spanIdProvider: SpanIdProvider,
): ManagedContextProvider {
    override fun provide(): ManagedContext {
        return ManagedContext(
            traceId = traceIdProvider.provideTraceId(),
            causationId = causationIdProvider.provideCausationId(),
            issuerProvider = { issuerProvider.currentIssuer() },
            spanIdProvider = spanIdProvider,
        )
    }
}