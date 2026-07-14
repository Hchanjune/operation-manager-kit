package io.github.hchanjune.omk.core.annotations

import java.lang.annotation.Inherited

// @Inherited: subclasses of an annotated base (e.g. an abstract cache-repository base class)
// are matched by the @within pointcut for methods they declare themselves.
@Inherited
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedCacheRepository(val description: String = "")
