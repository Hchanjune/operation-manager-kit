package io.github.hchanjune.omk.core.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedSchedule(val description: String = "")
