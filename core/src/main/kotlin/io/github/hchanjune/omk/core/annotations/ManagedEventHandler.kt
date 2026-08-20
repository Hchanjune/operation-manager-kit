package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: `[ENT]` ENTRY — opens a **root** span for a messaging handler and sets
 * `executionScope` to `EVENT`. A message is an entry point into the application just as an HTTP
 * request is, so it starts its own trace rather than hanging off one.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedEventHandler(val description: String = "")