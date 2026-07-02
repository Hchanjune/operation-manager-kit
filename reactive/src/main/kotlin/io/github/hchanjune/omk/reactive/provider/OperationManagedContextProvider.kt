package io.github.hchanjune.omk.reactive.provider

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider

class OperationManagedContextProvider(
    private val spanIdProvider: SpanIdProvider
) : ManagedContextProvider {
    override fun provide(): ManagedContext = ManagedContext(spanIdProvider = spanIdProvider)
}
