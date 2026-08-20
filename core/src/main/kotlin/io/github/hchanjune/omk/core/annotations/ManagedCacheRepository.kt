package io.github.hchanjune.omk.core.annotations

import java.lang.annotation.Inherited

/**
 * Span layer: `[CAC]` CACHE — opens a child span for **every** method of the annotated class.
 *
 * Same mechanics as [ManagedRepository], but a separate layer so cache traffic stays
 * distinguishable from database traffic in span trees and metrics.
 */
// @Inherited: subclasses of an annotated base (e.g. an abstract cache-repository base class)
// are matched by the @within pointcut for methods they declare themselves.
@Inherited
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedCacheRepository(val description: String = "")
