package io.github.hchanjune.omk.core.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedService(val description: String = "")