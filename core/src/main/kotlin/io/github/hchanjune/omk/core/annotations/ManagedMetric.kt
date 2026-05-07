package io.github.hchanjune.omk.core.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ManagedMetric(
    val name: String = "" // fallback: ClassName.methodName
)
