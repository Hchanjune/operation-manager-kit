package io.github.hchanjune.omk.core.context

import io.github.hchanjune.omk.core.contants.ManagedProtocolType
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import java.time.Clock

class ManagedContext(
    val traceId: String,
    val causationId: String = "",
    val issuer: String,
    private val clock: Clock = Clock.systemUTC(),
    private val spanIdProvider: SpanIdProvider
) {

    // HTTP, GRPC, KAFKA, LOCAL
    var protocol: ManagedProtocolType = ManagedProtocolType.UNSUPPORTED
        private set
    // API, WEBHOOK, COMMAND, EVENT, BATCH, SCHEDULED
    var type: String = "Type not injected yet."
        private set
    var uri: String = "Http URI not injected yet."
        private set
    var method: String = "Http Method not injected yet."
        private set
    var entrypoint: String = "Entrypoint not injected yet."
        private set
    var service: String = "Service not injected yet."
        private set
    var operation: String = "Operation not injected yet."
        private set
    var useCase: String = "UseCase not injected yet."
        private set
    var message: String = "Operation Managed"

    var startMillis: Long = 0L
        private set
    var endMillis: Long = 0L
        private set
    var durationMs: Long = 0L
        private set


    fun start() {
        this.startMillis = clock.millis()
    }

    fun end() {
        if (this.startMillis == 0L) return
        this.endMillis = clock.millis()
        this.durationMs = endMillis - startMillis
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

}