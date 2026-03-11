package io.github.hchanjune.omk.core.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedController(val description: String = "")