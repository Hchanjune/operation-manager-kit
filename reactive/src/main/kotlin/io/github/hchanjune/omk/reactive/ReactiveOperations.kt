package io.github.hchanjune.omk.reactive

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import io.github.hchanjune.omk.reactive.aspect.ReactiveAspectSupport
import io.github.hchanjune.omk.reactive.async.ManagedContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

object ReactiveOperations {

    val CONTEXT_KEY: Any = ManagedContext::class.java

    private val logger = LoggerFactory.getLogger(ReactiveOperations::class.java)

    // Configuration reads resolve through the runtime attached to the current context first
    // (set by the entry point that opened it), falling back to this static default. The
    // default is what configure*() writes to, so single-context apps behave exactly as before;
    // multiple Spring contexts in one JVM stop clobbering each other's configuration.
    private var defaultRuntime = OperationRuntime()

    val hook: OperationHook?
        get() = defaultRuntime.hook

    internal fun hookFor(context: ManagedContext): OperationHook? =
        context.runtime?.hook ?: defaultRuntime.hook

    /**
     * The [ManagedContext] governing the current execution, or `null` when there is none.
     *
     * Resolution order, innermost scope first:
     * 1. a [ManagedContextElement] installed on this coroutine — an explicitly opened async scope
     *    owns its forked context,
     * 2. the Reactor context of the pipeline this coroutine belongs to,
     * 3. the thread-bound context installed by a blocking entry point
     *    (`@ManagedEventHandler`, `@ManagedSchedule`).
     *
     * Unlike [invoke], this never falls back to a detached context: an absent context is reported
     * as `null` so the caller can decide. Used by [managedContextElement] to hand the current
     * context to a coroutine that leaves this thread.
     */
    suspend fun currentContextOrNull(): ManagedContext? {
        val coroutineContext = currentCoroutineContext()
        coroutineContext[ManagedContextElement]?.let { return it.managedContext }
        return coroutineContext[ReactorContext]?.context
            ?.getOrEmpty<ManagedContext>(CONTEXT_KEY)?.orElse(null)
            ?: ReactiveAspectSupport.threadBoundContext.get()
    }

    suspend operator fun <T> invoke(block: suspend ManagedContext.() -> T): OperationResult<T> {
        val managedContext = currentContextOrNull()
            ?: detachedContext().also { logDetached() }
        val data = managedContext.block()
        managedContext.injectResponse(data?.toString() ?: "null")
        return OperationResult(context = managedContext, data = data)
    }

    fun <T : Any> mono(block: ManagedContext.() -> Mono<T>): Mono<OperationResult<T>> =
        Mono.deferContextual { ctx ->
            val managedContext = ctx.getOrEmpty<ManagedContext>(CONTEXT_KEY).orElse(null)
                ?: ReactiveAspectSupport.threadBoundContext.get()
                ?: detachedContext().also { logDetached() }
            managedContext.block().map { data ->
                managedContext.injectResponse(data.toString())
                OperationResult(context = managedContext, data = data)
            }
        }

    // Fallback when code runs outside a managed reactive scope: observability degrades to
    // a warn log instead of failing the business logic. The context is not written to the
    // Reactor context, so it is never propagated to other executions.
    private fun detachedContext(): ManagedContext =
        (defaultRuntime.contextProvider?.provide() ?: ManagedContext(spanIdProvider = DETACHED_SPAN_ID_PROVIDER))
            .apply {
                defaultRuntime.traceIdProvider?.let { injectTraceId(it.provideTraceId()) }
                defaultRuntime.causationIdProvider?.let { injectCausationId(it.provideCausationId()) }
                start()
            }

    private val DETACHED_SPAN_ID_PROVIDER = SpanIdProvider { "detached" }

    private fun logDetached() {
        logger.warn(
            "No ManagedContext found. Proceeding with a detached (unmanaged) context — " +
                "spans and hooks will not be recorded for this execution. " +
                "Annotate the entry point (@ManagedSchedule, @ManagedEventHandler) " +
                "or ensure the OMK context filter covers this request."
        )
    }

    fun configureHook(hook: OperationHook) {
        defaultRuntime.hook = hook
    }

    fun configureEventProviders(
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        generateWhenMissing: Boolean
    ) {
        defaultRuntime.contextProvider = contextProvider
        defaultRuntime.traceIdProvider = traceIdProvider
        defaultRuntime.causationIdProvider = causationIdProvider
        defaultRuntime.generateWhenMissing = generateWhenMissing
    }

    internal fun configureDefaultRuntime(runtime: OperationRuntime) {
        defaultRuntime = runtime
    }

    internal fun initializeForEvent(metadata: EventMetadata, runtime: OperationRuntime? = null): ManagedContext {
        val rt = runtime ?: defaultRuntime
        val provider = rt.contextProvider
            ?: error("Event providers not configured. Ensure OMK WebFlux is properly set up.")
        return provider.provide().apply {
            injectTraceId(
                metadata.traceId ?: if (rt.generateWhenMissing) rt.traceIdProvider?.provideTraceId() ?: "" else ""
            )
            injectCausationId(
                metadata.causationId ?: if (rt.generateWhenMissing) rt.causationIdProvider?.provideCausationId() ?: "" else ""
            )
            if (metadata.traceId != null) markTraceContinuedFromRemote()
            metadata.issuer?.let { injectIssuer(it) }
            metadata.eventType?.let { injectType(it) }
            injectProtocol("MESSAGING")
            markAsEvent()
            this.runtime = rt
            if (rt.defaultAsyncHookEnabled) enableAsyncHook()
            start()
        }
    }

    internal fun initializeForSchedule(runtime: OperationRuntime? = null): ManagedContext {
        val rt = runtime ?: defaultRuntime
        val provider = rt.contextProvider
            ?: error("Event providers not configured. Ensure OMK WebFlux is properly set up.")
        return provider.provide().apply {
            injectTraceId(rt.traceIdProvider?.provideTraceId() ?: "")
            injectCausationId(rt.causationIdProvider?.provideCausationId() ?: "")
            injectType("SCHEDULED")
            injectProtocol("SCHEDULED")
            markAsScheduled()
            this.runtime = rt
            if (rt.defaultAsyncHookEnabled) enableAsyncHook()
            start()
        }
    }
}
