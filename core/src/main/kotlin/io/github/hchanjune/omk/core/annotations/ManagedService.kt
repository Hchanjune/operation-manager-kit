package io.github.hchanjune.omk.core.annotations

import java.lang.annotation.Inherited

/**
 * Span layer: **none** — this is the one Managed* annotation that opens no span. It only injects
 * the class name into `ManagedContext.service`, which then appears as the `service` tag on the
 * spans opened inside it.
 */
// @Inherited: subclasses of an annotated base class are matched by the @within pointcut
// for methods they declare themselves.
@Inherited
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedService(val description: String = "")