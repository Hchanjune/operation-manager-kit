package io.github.hchanjune.omk.core.event

data class EventMetadata(
    val traceId: String? = null,
    val causationId: String? = null,
    val issuer: String? = null,
    val eventType: String? = null
) {
    companion object {
        fun empty() = EventMetadata()
    }
}
