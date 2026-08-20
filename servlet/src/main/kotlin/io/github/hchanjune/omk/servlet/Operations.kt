package io.github.hchanjune.omk.servlet

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.context.ManagedContextHolder
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

object Operations: ManagedContextHolder {

    private val logger = LoggerFactory.getLogger(Operations::class.java)

    private val contextHolder = ThreadLocal<ManagedContext>()

    private val executorFallbackWarned = AtomicBoolean(false)

    // Configuration reads resolve through the runtime attached to the current context first
    // (set by the entry point that opened it), falling back to this static default. The
    // default is what configure*() writes to, so single-context apps behave exactly as before;
    // multiple Spring contexts in one JVM stop clobbering each other's configuration.
    private var defaultRuntime = OperationRuntime()

    override val hook: OperationHook?
        get() = contextHolder.get()?.runtime?.hook ?: defaultRuntime.hook

    override val defaultAsyncHookEnabled: Boolean
        get() = defaultRuntime.defaultAsyncHookEnabled

    override val context: ManagedContext
        get() = contextHolder.get()
            ?: detachedContext().also {
                logger.warn(
                    "No ManagedContext found. Proceeding with a detached (unmanaged) context — " +
                        "spans and hooks will not be recorded for this execution. " +
                        "Annotate the entry point (@ManagedSchedule, @ManagedEventHandler) " +
                        "or ensure the OMK context filter covers this request."
                )
            }

    // Fallback when code runs outside a managed scope: observability degrades to a warn
    // log instead of failing the business logic. The context is not stored in the holder,
    // so it is never leaked to other executions on the same thread.
    private fun detachedContext(): ManagedContext =
        (defaultRuntime.contextProvider?.provide() ?: ManagedContext(spanIdProvider = DETACHED_SPAN_ID_PROVIDER))
            .apply {
                defaultRuntime.traceIdProvider?.let { injectTraceId(it.provideTraceId()) }
                defaultRuntime.causationIdProvider?.let { injectCausationId(it.provideCausationId()) }
                start()
            }

    private val DETACHED_SPAN_ID_PROVIDER = SpanIdProvider { "detached" }

    override val hasContext: Boolean
        get() = contextHolder.get() != null

    override fun applyContext(context: ManagedContext) {
        contextHolder.set(context)
    }

    override fun initialize(context: ManagedContext) {
        if (context.runtime == null) context.runtime = defaultRuntime
        if (context.runtime?.defaultAsyncHookEnabled == true) context.enableAsyncHook()
        contextHolder.set(context)
        context.start()
    }

    override fun complete() {
        contextHolder.get()?.end()
    }

    override fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T> {
        val ctx = context
        val exe = ctx.runtime?.executor ?: defaultRuntime.executor ?: fallbackExecutor()
        return exe.run(ctx, block)
    }

    // Last-resort executor for code reaching Operations before (or without) OMK's
    // auto-configuration — most often a plain unit test constructing a @ManagedService
    // directly. Business logic keeps running and only observability degrades, matching how
    // a missing ManagedContext is handled in `context` above. The executor holds no
    // configuration of its own (the runtime owns the hook and providers), so this instance
    // is equivalent to the one the Spring configuration would have supplied. It is stored
    // on the default runtime so the warning is emitted once per JVM rather than on every
    // operation; the detached-context warning above already fires per execution.
    private fun fallbackExecutor(): OperationExecutor {
        if (executorFallbackWarned.compareAndSet(false, true)) {
            logger.warn(
                "OperationExecutor not configured — falling back to a default OperationExecutor(). " +
                    "Business logic runs normally, but this execution is outside OMK's configured " +
                    "runtime, so spans, hooks and metrics are NOT recorded for it. " +
                    "In a Spring application the executor is wired automatically: check that the OMK " +
                    "servlet starter is on the classpath and that its auto-configuration is not " +
                    "excluded — if you are seeing this outside of a test, treat it as a " +
                    "misconfiguration and fix it promptly. " +
                    "In a plain unit test that instantiates a @ManagedService directly this warning " +
                    "is expected and safe to ignore; call Operations.configure(OperationExecutor()) " +
                    "in your test setup if you would rather silence it. " +
                    "This warning is logged once per JVM."
            )
        }
        return OperationExecutor().also { defaultRuntime.executor = it }
    }

    override fun initializeForEvent(metadata: EventMetadata) = initializeForEvent(metadata, null)

    fun initializeForEvent(metadata: EventMetadata, runtime: OperationRuntime?) {
        val rt = runtime ?: defaultRuntime
        val provider = rt.contextProvider
            ?: error("Event providers not configured. Ensure OMK is properly set up.")
        val context = provider.provide().apply {
            injectTraceId(metadata.traceId ?: if (rt.generateWhenMissing) rt.traceIdProvider?.provideTraceId() ?: "" else "")
            injectCausationId(metadata.causationId ?: if (rt.generateWhenMissing) rt.causationIdProvider?.provideCausationId() ?: "" else "")
            if (metadata.traceId != null) markTraceContinuedFromRemote()
            metadata.issuer?.let { injectIssuer(it) }
            metadata.eventType?.let { injectType(it) }
            injectProtocol("MESSAGING")
            markAsEvent()
        }
        context.runtime = rt
        initialize(context)
    }

    override fun initializeForSchedule() = initializeForSchedule(null)

    fun initializeForSchedule(runtime: OperationRuntime?) {
        val rt = runtime ?: defaultRuntime
        val provider = rt.contextProvider
            ?: error("Event providers not configured. Ensure OMK is properly set up.")
        val context = provider.provide().apply {
            injectTraceId(rt.traceIdProvider?.provideTraceId() ?: "")
            injectCausationId(rt.causationIdProvider?.provideCausationId() ?: "")
            injectType("SCHEDULED")
            injectProtocol("SCHEDULED")
            markAsScheduled()
        }
        context.runtime = rt
        initialize(context)
    }

    override fun configure(executor: OperationExecutor) {
        defaultRuntime.executor = executor
    }

    override fun configureEventProviders(
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

    override fun configureHook(hook: OperationHook) {
        defaultRuntime.hook = hook
    }

    override fun configureDefaultAsyncHookEnabled(enabled: Boolean) {
        defaultRuntime.defaultAsyncHookEnabled = enabled
    }

    internal fun configureDefaultRuntime(runtime: OperationRuntime) {
        defaultRuntime = runtime
    }

    override fun clear() {
        contextHolder.remove()
    }

}
