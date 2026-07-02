package io.github.hchanjune.omk.core.event

abstract class AbstractTraceableEvent : TraceableEvent {
    override val traceId: String? = null
    override val causationId: String? = null
    override val issuer: String? = null
}
