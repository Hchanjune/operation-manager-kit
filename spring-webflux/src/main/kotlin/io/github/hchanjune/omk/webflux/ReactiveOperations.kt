package io.github.hchanjune.omk.webflux

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import reactor.core.publisher.Mono

object ReactiveOperations {

    val CONTEXT_KEY: Any = ManagedContext::class.java

    private var eventContextProvider: ManagedContextProvider? = null
    private var eventTraceIdProvider: TraceIdProvider? = null
    private var eventCausationIdProvider: CausationIdProvider? = null
    private var eventGenerateWhenMissing: Boolean = true

    var hook: OperationHook? = null
        private set

    suspend operator fun <T> invoke(block: suspend ManagedContext.() -> T): OperationResult<T> {
        val reactorCtx = currentCoroutineContext()[ReactorContext]?.context
            ?: error("No ReactorContext found. ReactiveOperations must be called within a managed reactive scope.")
        val managedContext = reactorCtx.getOrEmpty<ManagedContext>(CONTEXT_KEY).orElse(null)
            ?: error("No ManagedContext found. ReactiveOperations must be called within a managed reactive scope.")
        val data = managedContext.block()
        managedContext.injectResponse(data?.toString() ?: "null")
        return OperationResult(context = managedContext, data = data)
    }

    fun <T : Any> mono(block: ManagedContext.() -> Mono<T>): Mono<OperationResult<T>> =
        Mono.deferContextual { ctx ->
            val managedContext = ctx.getOrEmpty<ManagedContext>(CONTEXT_KEY).orElse(null)
                ?: return@deferContextual Mono.error(IllegalStateException(
                    "No ManagedContext found. ReactiveOperations must be called within a managed reactive scope."
                ))
            managedContext.block().map { data ->
                managedContext.injectResponse(data.toString())
                OperationResult(context = managedContext, data = data)
            }
        }

    fun configureHook(hook: OperationHook) {
        this.hook = hook
    }

    fun configureEventProviders(
        contextProvider: ManagedContextProvider,
        traceIdProvider: TraceIdProvider,
        causationIdProvider: CausationIdProvider,
        generateWhenMissing: Boolean
    ) {
        this.eventContextProvider = contextProvider
        this.eventTraceIdProvider = traceIdProvider
        this.eventCausationIdProvider = causationIdProvider
        this.eventGenerateWhenMissing = generateWhenMissing
    }

    internal fun initializeForEvent(metadata: EventMetadata): ManagedContext {
        val provider = eventContextProvider
            ?: error("Event providers not configured. Ensure OMK WebFlux is properly set up.")
        return provider.provide().apply {
            injectTraceId(
                metadata.traceId ?: if (eventGenerateWhenMissing) eventTraceIdProvider?.provideTraceId() ?: "" else ""
            )
            injectCausationId(
                metadata.causationId ?: if (eventGenerateWhenMissing) eventCausationIdProvider?.provideCausationId() ?: "" else ""
            )
            metadata.issuer?.let { injectIssuer(it) }
            metadata.eventType?.let { injectType(it) }
            injectProtocol("MESSAGING")
            markAsEvent()
            start()
        }
    }
}
