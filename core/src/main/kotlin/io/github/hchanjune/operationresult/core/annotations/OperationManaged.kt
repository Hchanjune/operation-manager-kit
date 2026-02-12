package io.github.hchanjune.operationresult.core.annotations

/**
 * Marks a class or method as "operation-managed".
 *
 * ## Purpose
 * This annotation is used as an opt-in switch for OperationManagerKit features.
 * When applied, the library captures invocation context (e.g. service/function)
 * and stores it into MDC so that [InvocationInfoProvider] can read it.
 *
 * ## Where it works
 * - This annotation is primarily consumed by Spring AOP-based modules (e.g. webmvc, webflux).
 * - It is effective only when the target object is a Spring-managed bean and AOP is enabled.
 *
 * ## Notes / Limitations
 * - Self-invocation (calling another method within the same class) may bypass Spring AOP proxies,
 *   so advice might not run for the inner call.
 * - MDC values are thread-local and may not propagate across async boundaries unless explicitly configured.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class OperationManaged(
    val operation: String = "",
    val useCase: String = "",
    val event: String = ""
)