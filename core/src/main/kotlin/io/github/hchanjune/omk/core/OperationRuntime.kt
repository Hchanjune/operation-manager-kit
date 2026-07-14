package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider

/**
 * The mutable configuration bundle (executor, hook, context providers) that used to live as
 * global fields on Operations/ReactiveOperations.
 *
 * Each Spring context assembles its own instance and hands it to the entry-point beans
 * (filters, event/schedule aspects), which attach it to every [io.github.hchanjune.omk.core.context.ManagedContext]
 * they create. Downstream reads resolve through the attached runtime first and fall back to the
 * static default, so multiple Spring contexts in one JVM no longer clobber each other's
 * configuration. Wiring detail — not intended to be touched by application code.
 */
class OperationRuntime {
    var executor: OperationExecutor? = null
    var hook: OperationHook? = null
    var defaultAsyncHookEnabled: Boolean = false
    var contextProvider: ManagedContextProvider? = null
    var traceIdProvider: TraceIdProvider? = null
    var causationIdProvider: CausationIdProvider? = null
    var generateWhenMissing: Boolean = true
}
