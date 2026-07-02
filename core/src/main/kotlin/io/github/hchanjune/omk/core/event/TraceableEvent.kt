package io.github.hchanjune.omk.core.event

import io.github.hchanjune.omk.core.annotations.ManagedEventCausationId
import io.github.hchanjune.omk.core.annotations.ManagedEventIssuer
import io.github.hchanjune.omk.core.annotations.ManagedEventTraceId
import io.github.hchanjune.omk.core.annotations.ManagedEventType

interface TraceableEvent {
    @get:ManagedEventTraceId
    val traceId: String?

    @get:ManagedEventCausationId
    val causationId: String?

    @get:ManagedEventIssuer
    val issuer: String?

    @get:ManagedEventType
    val eventType: String
}
