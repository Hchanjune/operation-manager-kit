package io.github.hchanjune.omk.webmvc.provider

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider

class OperationManagedContextProvider(
    private val spanIdProvider: SpanIdProvider,
): ManagedContextProvider {
    override fun provide(): ManagedContext {
        return ManagedContext(
            spanIdProvider = spanIdProvider,
        )
    }
}