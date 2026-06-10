package io.github.hchanjune.omk.core.context

import io.github.hchanjune.omk.core.contants.ExecutionScope
import io.github.hchanjune.omk.core.contants.ManagedProtocolType
import io.github.hchanjune.omk.core.contants.OperationOutcome
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import java.time.Clock
import java.time.Instant

class ManagedContext(
    private val clock: Clock = Clock.systemUTC(),
    private val spanIdProvider: SpanIdProvider
) {

    var traceId: String = ""

    var causationId: String = ""

    var issuer: String = ""

    var executionScope: ExecutionScope = ExecutionScope.PRIMARY
        private set

    val isAsync: Boolean get() = executionScope == ExecutionScope.ASYNC
    val isEvent: Boolean get() = executionScope == ExecutionScope.EVENT

    var isAsyncHookEnabled: Boolean = false
        private set

    fun enableAsyncHook() { isAsyncHookEnabled = true }
    fun disableAsyncHook() { isAsyncHookEnabled = false }
    fun markAsEvent() { executionScope = ExecutionScope.EVENT }

    // HTTP, GRPC, KAFKA, LOCAL
    var protocol: ManagedProtocolType = ManagedProtocolType.UNSUPPORTED
        private set
    // API, WEBHOOK, COMMAND, EVENT, BATCH, SCHEDULED

    var type: String = ""
        private set

    var uri: String = ""
        private set

    var method: String = ""
        private set

    var entrypoint: String = ""
        private set

    var service: String = ""
        private set

    var operation: String = ""
        private set

    var useCase: String = ""
        private set

    var response: String = ""
        private set

    var statusCode: Int? = null
        private set

    var outcome: OperationOutcome = OperationOutcome.SUCCESS
        private set

    var message: String = "Operation Managed"

    val timestamp: Instant = Instant.now(clock)

    var startMillis: Long = 0L
        private set
    var endMillis: Long = 0L
        private set
    var durationMs: Long = 0L
        private set


    fun start() {
        if (this.startMillis != 0L) return
        this.startMillis = clock.millis()
    }

    fun end() {
        if (this.startMillis == 0L || this.endMillis != 0L) return
        this.endMillis = clock.millis()
        this.durationMs = endMillis - startMillis
    }

    fun injectTraceId(traceId: String) {
        this.traceId = traceId
    }

    fun injectCausationId(causationId: String) {
        this.causationId = causationId
    }

    fun injectIssuer(issuer: String) {
        this.issuer = issuer
    }

    fun injectProtocol(protocol: String) {
        this.protocol = ManagedProtocolType.from(protocol)
    }

    fun injectType(type: String) {
        this.type = type
    }

    fun injectHttpInfo(uri: String, method: String) {
        this.uri = uri
        this.method = method
    }

    fun injectEntryPoint(entrypoint: String) {
        this.entrypoint = entrypoint
    }

    fun injectService(service: String) {
        this.service = service
    }

    fun injectAnnotationInfo(operation:String, useCase: String) {
        this.operation = operation
        this.useCase = useCase
    }

    fun injectResponse(response:String) {
        this.response = response
    }

    fun injectStatusCode(statusCode: Int) {
        this.statusCode = statusCode
        this.outcome = OperationOutcome.fromStatusCode(statusCode)
    }


    private val spanStack = ArrayDeque<MetricSpan>()

    var rootSpan: MetricSpan? = null
        private set

    fun push(
        name: MetricName,
        kind: MetricKind,
        policy: MetricPolicy,
        tags: MetricTags,
        descriptor: MetricDescriptor,
        idProvider: SpanIdProvider
    ): MetricSpan {
        val newSpan = MetricSpan(
            traceId = this.traceId,
            spanId = idProvider.provideSpanId(),
            name = name,
            kind = kind,
            policy = policy,
            tags = tags,
            descriptor = descriptor,
            clock = clock
        )

        if (spanStack.isEmpty()) {
            rootSpan = newSpan
        } else {
            spanStack.firstOrNull()?.addChild(newSpan)
        }

        spanStack.addFirst(newSpan)
        return newSpan
    }

    fun peek(): MetricSpan? = spanStack.firstOrNull()
    fun pop(): MetricSpan? = if (spanStack.isNotEmpty()) spanStack.removeFirst() else null
    fun isFinished(): Boolean = spanStack.isEmpty()

    fun forkAsync(): ManagedContext {
        val child = ManagedContext(clock, spanIdProvider)
        child.traceId = this.traceId
        child.causationId = this.causationId
        child.issuer = this.issuer
        child.protocol = this.protocol
        child.type = this.type
        child.uri = this.uri
        child.method = this.method
        child.entrypoint = this.entrypoint
        child.service = this.service
        child.operation = this.operation
        child.useCase = this.useCase
        child.message = this.message
        child.executionScope = ExecutionScope.ASYNC
        child.isAsyncHookEnabled = this.isAsyncHookEnabled
        child.push(
            name = MetricName("async.execution"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.empty(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )
        return child
    }

    data class HookRecord(
        val hookName: String,
        val success: Boolean,
        val error: Throwable? = null
    )

    private val _hookRecords = mutableListOf<HookRecord>()
    val hookRecords: List<HookRecord> get() = _hookRecords

    fun recordHookSuccess(hookName: String) {
        _hookRecords.add(HookRecord(hookName = hookName, success = true))
    }

    fun recordHookFailure(hookName: String, error: Throwable) {
        _hookRecords.add(HookRecord(hookName = hookName, success = false, error = error))
    }

    private var metricsRecorded = false
    fun isMetricsRecorded(): Boolean = metricsRecorded
    fun markMetricsRecorded() { metricsRecorded = true }

    private var hooksExecuted = false
    fun isHooksExecuted(): Boolean = hooksExecuted
    fun markHooksExecuted() { hooksExecuted = true }

}