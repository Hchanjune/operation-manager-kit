package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: `[APP]` APPLICATION — opens a named child span for any method.
 *
 * Same layer as [ManagedOperation], but purely a measurement: it does not define `operation`
 * or `useCase`, it inherits them from the enclosing context.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ManagedMetric(
    val name: String = "" // fallback: ClassName.methodName
)
