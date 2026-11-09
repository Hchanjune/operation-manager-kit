package io.github.hchanjune.omk.core.context

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.bridge.BridgedSpan
import io.github.hchanjune.omk.core.bridge.BridgedSpanHold
import io.github.hchanjune.omk.core.bridge.BridgedTrace
import io.github.hchanjune.omk.core.bridge.SpanBridge
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
        private set

    var causationId: String = ""
        private set

    /**
     * True when this context's traceId/causationId were extracted from an incoming carrier
     * (traceparent header, event envelope) rather than self-generated. A [SpanBridge] uses
     * this to decide between continuing the remote trace and starting a fresh one.
     */
    var traceContinuedFromRemote: Boolean = false
        private set

    fun markTraceContinuedFromRemote() { traceContinuedFromRemote = true }

    var issuer: String = ""
        private set

    var ip: String = ""
        private set

    var deviceId: String = "NOT_SUPPORTED_YET"
        private set

    var deviceInfo: String = "NOT_SUPPORTED_YET"
        private set

    var executionScope: ExecutionScope = ExecutionScope.PRIMARY
        private set

    val isAsync: Boolean get() = executionScope == ExecutionScope.ASYNC
    val isEvent: Boolean get() = executionScope == ExecutionScope.EVENT
    val isScheduled: Boolean get() = executionScope == ExecutionScope.SCHEDULED

    var isAsyncHookEnabled: Boolean = false
        private set

    fun enableAsyncHook() { isAsyncHookEnabled = true }
    fun disableAsyncHook() { isAsyncHookEnabled = false }
    fun markAsEvent() { executionScope = ExecutionScope.EVENT }
    fun markAsScheduled() { executionScope = ExecutionScope.SCHEDULED }

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

    // Set to false inside an Operations { } block to silence the default success log
    // for this execution only. Failures (and captured exceptions) are always logged.
    var defaultLogging: Boolean = true

    // The configuration bundle of the Spring context that opened this ManagedContext.
    // Attached by entry points (filter, event/schedule aspects); reads fall back to the
    // static default runtime when absent. Wiring detail — not for application code.
    var runtime: OperationRuntime? = null

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

    fun injectIp(ip: String) {
        this.ip = ip
    }

    fun injectDeviceId(deviceId: String) {
        this.deviceId = deviceId
    }

    fun injectDeviceInfo(deviceInfo: String) {
        this.deviceInfo = deviceInfo
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

    var capturedException: Throwable? = null
        private set

    /**
     * Records the exception a @ExceptionHandler/@ControllerAdvice is about to convert into a response,
     * before its identity is lost. First call wins, since it's closest to the real throw site.
     */
    fun recordException(exception: Throwable) {
        if (this.capturedException == null) this.capturedException = exception
    }


    private val spanStack = ArrayDeque<MetricSpan>()

    var rootSpan: MetricSpan? = null
        private set

    // Backend root context, created lazily by the bridge on the first push.
    private var bridgedTrace: BridgedTrace? = null

    // Set while this execution's backend spans wait for the entry point's export decision.
    private var bridgedSpanHold: BridgedSpanHold? = null

    /**
     * Holds back the backend end of every span from now on, until [releaseBridgedSpans] or
     * [discardBridgedSpans]. Call before the first [push]. Metrics and logs are not affected —
     * only what reaches the trace backend. No effect without a [SpanBridge].
     *
     * 지금부터 모든 span 의 백엔드 종료를 [releaseBridgedSpans] 또는 [discardBridgedSpans] 까지 미룬다.
     * 첫 [push] 전에 호출한다. 메트릭·로그는 영향이 없고 trace 백엔드로 가는 것만 달라진다.
     * [SpanBridge] 가 없으면 아무 일도 하지 않는다.
     */
    fun holdBridgedSpans() {
        if (bridgedSpanHold == null && runtime?.spanBridge != null) bridgedSpanHold = BridgedSpanHold()
    }

    /** Exports the held spans with their real end times. 미뤄 둔 span 을 실제 종료 시각으로 내보낸다. */
    fun releaseBridgedSpans() {
        bridgedSpanHold?.release()
    }

    /** Drops the held spans: never ended, so never exported. 미뤄 둔 span 을 버린다(끝내지 않으므로 내보내지 않는다). */
    fun discardBridgedSpans() {
        bridgedSpanHold?.discard()
    }

    fun push(
        name: MetricName,
        kind: MetricKind,
        policy: MetricPolicy,
        tags: MetricTags,
        descriptor: MetricDescriptor,
        idProvider: SpanIdProvider
    ): MetricSpan {
        val bridged = startBridgedSpan(name, tags, descriptor)

        val newSpan = MetricSpan(
            traceId = this.traceId,
            spanId = bridged?.spanId ?: idProvider.provideSpanId(),
            name = name,
            kind = kind,
            policy = policy,
            tags = tags,
            descriptor = descriptor,
            clock = clock
        )
        if (bridged != null) {
            newSpan.bridge = runtime?.spanBridge
            newSpan.bridgeHandle = bridged
            newSpan.bridgeHold = bridgedSpanHold
        }

        if (spanStack.isEmpty()) {
            rootSpan = newSpan
        } else {
            spanStack.firstOrNull()?.addChild(newSpan)
        }

        spanStack.addFirst(newSpan)
        return newSpan
    }

    /**
     * Starts a live backend span when a [SpanBridge] is attached. On the first (root) push the
     * bridge also establishes the backend trace; when the trace was not continued from a remote
     * carrier, the backend-generated traceId is adopted back into this context so OMK logs and
     * the trace backend agree on one id. Fail-open: any bridge error falls back to OMK-only.
     */
    private fun startBridgedSpan(
        name: MetricName,
        tags: MetricTags,
        descriptor: MetricDescriptor
    ): BridgedSpan? {
        val bridge = runtime?.spanBridge ?: return null
        return try {
            val trace = bridgedTrace ?: bridge.startTrace(this).also { bridgedTrace = it }
            val parentHandle = spanStack.firstOrNull()?.bridgeHandle
            val handle = bridge.startSpan(trace, name.value, descriptor.layer, tags, parentHandle)
            if (spanStack.isEmpty() && !traceContinuedFromRemote && handle.traceId.isNotBlank()) {
                traceId = handle.traceId
            }
            handle
        } catch (e: Throwable) {
            log.warn("Span bridge failed to start span [{}], falling back to OMK-only: {}", name.value, e.toString())
            null
        }
    }

    fun peek(): MetricSpan? = spanStack.firstOrNull()
    fun pop(): MetricSpan? = if (spanStack.isNotEmpty()) spanStack.removeFirst() else null
    fun isFinished(): Boolean = spanStack.isEmpty()

    fun forkAsync(): ManagedContext {
        val child = ManagedContext(clock, spanIdProvider)
        child.traceId = this.traceId
        child.causationId = this.causationId
        child.issuer = this.issuer
        child.ip = this.ip
        child.protocol = this.protocol
        child.type = this.type
        child.uri = this.uri
        child.method = this.method
        child.entrypoint = this.entrypoint
        child.service = this.service
        child.operation = this.operation
        child.useCase = this.useCase
        child.message = this.message
        child.defaultLogging = this.defaultLogging
        child.runtime = this.runtime
        // The fork continues this context's trace — without this the bridge would treat the
        // child's first push as a fresh trace and clobber the copied traceId.
        child.traceContinuedFromRemote = true
        // Hand the live backend context of the forking span to the child, so the fork's spans
        // parent under it (same backend trace) instead of starting a disconnected one.
        child.bridgedTrace = (spanStack.firstOrNull()?.bridgeHandle?.nativeContext ?: bridgedTrace?.nativeContext)
            ?.let { BridgedTrace(it) }
        // The fork belongs to the same execution, so its spans follow the same export decision.
        child.bridgedSpanHold = this.bridgedSpanHold
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

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ManagedContext::class.java)
    }
}