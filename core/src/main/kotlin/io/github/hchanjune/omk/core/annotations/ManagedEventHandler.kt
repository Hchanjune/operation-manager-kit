package io.github.hchanjune.omk.core.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedEventHandler(val description: String = "")