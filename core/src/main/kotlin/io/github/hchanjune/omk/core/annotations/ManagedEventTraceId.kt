package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: **none** — an extraction marker, not an instrumentation point. Tags the event field
 * whose value is read into `ManagedContext.traceId` when [ManagedEventHandler] opens its span.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedEventTraceId
