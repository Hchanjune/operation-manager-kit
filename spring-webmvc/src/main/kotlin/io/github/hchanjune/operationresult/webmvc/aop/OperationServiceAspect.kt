package io.github.hchanjune.operationresult.webmvc.aop

import io.github.hchanjune.operationresult.webmvc.constants.OperationMdcKeys
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.MDC

/**
 * Spring AOP aspect that captures service-level invocation metadata into MDC.
 *
 * ## Purpose
 * This aspect enriches the logging context by storing the current service class
 * and method name into MDC, allowing downstream components (such as
 * [MdcInvocationInfoProvider]) to build a consistent [InvocationInfo].
 *
 * ## Activation
 * The advice is applied only when the target method or class is annotated with
 * `@OperationManaged`.
 *
 * This provides an opt-in mechanism, avoiding the overhead of intercepting
 * every service method in the application.
 *
 * ## Captured MDC keys
 * - `service`: Simple name of the target service class
 * - `function`: Name of the invoked method
 *
 * ## Nested invocation safety
 * Since service calls may be nested (service A → service B), this aspect restores
 * previously existing MDC values after execution to prevent context loss.
 *
 * ## Requirements
 * - Spring AOP must be enabled (typically by including `spring-boot-starter-aop`)
 * - The target must be a Spring-managed bean
 *
 * ## Limitations
 * - Self-invocation (method calls within the same class) may bypass proxies,
 *   meaning advice may not run for inner calls.
 * - MDC is thread-local; invocation metadata may not propagate automatically
 *   across async boundaries.
 */
@Aspect
class OperationServiceAspect {

    /**
     * Captures service and function names for operation-managed execution points.
     *
     * The advice matches when either:
     * - The enclosing class is annotated with `@OperationManaged`, or
     * - The method itself is annotated with `@OperationManaged`.
     */
    @Around(
        "(@within(io.github.hchanjune.operationresult.core.annotations.OperationManaged) || " +
                "@annotation(io.github.hchanjune.operationresult.core.annotations.OperationManaged)) && " +
                "execution(* *(..))"
    )
    fun captureService(joinPoint: ProceedingJoinPoint): Any? {
        val className = joinPoint.target?.javaClass?.simpleName
            ?: joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name

        val prevService = MDC.get(OperationMdcKeys.SERVICE)
        val prevFunction = MDC.get(OperationMdcKeys.FUNCTION)

        MDC.put(OperationMdcKeys.SERVICE, className)
        MDC.put(OperationMdcKeys.FUNCTION, methodName)

        return try {
            joinPoint.proceed()
        } finally {
            if (prevService == null) MDC.remove(OperationMdcKeys.SERVICE) else MDC.put(OperationMdcKeys.SERVICE, prevService)
            if (prevFunction == null) MDC.remove(OperationMdcKeys.FUNCTION) else MDC.put(OperationMdcKeys.FUNCTION, prevFunction)
        }

    }

}