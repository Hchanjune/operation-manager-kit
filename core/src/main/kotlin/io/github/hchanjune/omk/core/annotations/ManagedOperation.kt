package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: `[APP]` APPLICATION — opens a child span for the annotated method.
 *
 * This is also the annotation that *defines* `operation` and `useCase` for the context; every
 * child span opened beneath it inherits those values as tags.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ManagedOperation(
    val operation: String = "",
    val useCase: String = "",
)