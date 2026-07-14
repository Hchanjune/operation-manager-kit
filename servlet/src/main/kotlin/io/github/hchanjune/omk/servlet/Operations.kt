package io.github.hchanjune.omk.servlet

import io.github.hchanjune.omk.core.OperationExecutor
import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.OperationResult
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.context.ManagedContextHolder
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.provider.CausationIdProvider
import io.github.hchanjune.omk.core.provider.ManagedContextProvider
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.core.provider.TraceIdProvider
import org.slf4j.LoggerFactory

object Operations: ManagedContextHolder {

    private val logger = LoggerFactory.getLogger(Operations::class.java)

    private val contextHolder = ThreadLocal<ManagedContext>()

    private lateinit var executor: OperationExecutor
    private var eventContextProvider: ManagedContextProvider? = null
    private var eventTraceIdProvider: TraceIdProvider? = null
    private var eventCausationIdProvider: CausationIdProvider? = null
    private var eventGenerateWhenMissing: Boolean = true

    override var hook: OperationHook? = null
        private set

    override var defaultAsyncHookEnabled: Boolean = false
        private set

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
        (eventContextProvider?.provide() ?: ManagedContext(spanIdProvider = DETACHED_SPAN_ID_PROVIDER))
            .apply {
                eventTraceIdProvider?.let { injectTraceId(it.provideTraceId()) }
                eventCausationIdProvider?.let { injectCausationId(it.provideCausationId()) }
                start()
            }

    private val DETACHED_SPAN_ID_PROVIDER = SpanIdProvider { "detached" }

    override val hasContext: Boolean
        get() = contextHolder.get() != null

    override fun applyContext(context: ManagedContext) {
        contextHolder.set(context)
    }

    override fun initialize(context: ManagedContext) {
        if (defaultAsyncHookEnabled) context.enableAsyncHook()
        contextHolder.set(context)
        context.start()
    }

    override fun complete() {
        contextHolder.get()?.end()
    }

    override fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T> {
        val exe = executor
        return exe.run(context, block)
    }

    override fun initializeForEvent(metadata: EventMetadata) {
        val provider = eventContextProvider
            ?: error("Event providers not configured. Ensure OMK is properly set up.")
        val context = provider.provide().apply {
            injectTraceId(metadata.traceId ?: if (eventGenerateWhenMissing) eventTraceIdProvider?.provideTraceId() ?: "" else "")
            injectCausationId(metadata.causationId ?: if (eventGenerateWhenMissing) eventCausationIdProvider?.provideCausationId() ?: "" else "")
            metadata.issuer?.let { injectIssuer(it) }
            metadata.eventType?.let { injectType(it) }
            injectProtocol("MESSAGING")
            markAsEvent()
        }
        initialize(context)
    }

    override fun initializeForSchedule() {
        val provider = eventContextProvider
            ?: error("Event providers not configured. Ensure OMK is properly set up.")
        val context = provider.provide().apply {
            injectTraceId(eventTraceIdProvider?.provideTraceId() ?: "")
            injectCausationId(eventCausationIdProvider?.provideCausationId() ?: "")
            injectType("SCHEDULED")
            injectProtocol("SCHEDULED")
            markAsScheduled()
        }
        initialize(context)
    }

    override fun configure(executor: OperationExecutor) {
        this.executor = executor
    }

    override fun configureEventProviders(
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

    override fun configureHook(hook: OperationHook) {
        this.hook = hook
    }

    override fun configureDefaultAsyncHookEnabled(enabled: Boolean) {
        this.defaultAsyncHookEnabled = enabled
    }

    override fun clear() {
        contextHolder.remove()
    }

}
