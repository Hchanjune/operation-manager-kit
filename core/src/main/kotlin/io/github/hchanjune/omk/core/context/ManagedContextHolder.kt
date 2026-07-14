package io.github.hchanjune.omk.core.context

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider

interface ManagedContextHolder {
    val context: ManagedContext
    val hasContext: Boolean
    val hook: OperationHook?
    val defaultAsyncHookEnabled: Boolean
    fun initialize(context: ManagedContext)
    fun initializeForEvent(metadata: EventMetadata)
    fun initializeForSchedule()
    fun complete()
    fun configure(executor: OperationExecutor)
    fun configureHook(hook: OperationHook)
    fun configureDefaultAsyncHookEnabled(enabled: Boolean)
    fun configureEventProviders(
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        generateWhenMissing: Boolean
    )
    operator fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T>
    fun applyContext(context: ManagedContext)
    fun clear()
}
