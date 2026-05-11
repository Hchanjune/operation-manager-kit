package io.github.hchanjune.omk.core.annotations

/**
 * Marks a class or method as "operation-managed".
 *
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ManagedOperation(
    val operation: String = "",
    val useCase: String = "",
)