package io.github.hchanjune.omk.core.annotations

import java.lang.annotation.Inherited

/**
 * Span layer: `[DB ]` DB — opens a child span for **every** method of the annotated class.
 *
 * Class-level is safe here because the invariant holds: every method of a repository reaches its
 * store. Contrast [ManagedExternalCall], which is method-level for exactly that reason.
 */
// @Inherited: subclasses of an annotated base class are matched by the @within pointcut
// for methods they declare themselves.
@Inherited
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedRepository(val description: String = "")
