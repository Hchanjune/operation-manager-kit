package io.github.hchanjune.operationresult.webmvc.aop

import io.github.hchanjune.operationresult.core.annotations.OperationManaged
import io.github.hchanjune.operationresult.webmvc.constants.OperationMdcKeys
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.MDC
import org.springframework.aop.support.AopUtils
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.Order
import org.springframework.util.ReflectionUtils

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
 * - `operation`: Operation injected by Annotation `@OperationManaged`
 * - `useCase`: UseCase injected by Annotation `@OperationManaged`
 * - `event`: Event injected by Annotation `@OperationManaged`
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
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class OperationServiceAspect {

    /**
     * Captures service and function names for operation-managed execution points.
     *
     * The advice matches when either:
     * - The enclosing `class` is annotated with `@OperationManaged`.
     *
     * or
     * - The `method` itself is annotated with `@OperationManaged`.
     */
    @Around(
        "(@within(io.github.hchanjune.operationresult.core.annotations.OperationManaged) || " +
                "@annotation(io.github.hchanjune.operationresult.core.annotations.OperationManaged)) && " +
                "execution(* *(..))"
    )
    fun captureService(joinPoint: ProceedingJoinPoint): Any? {
        val targetClass = AopUtils.getTargetClass(joinPoint.target ?: joinPoint.signature.declaringType)
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method

        val methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, OperationManaged::class.java)
        val classAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, OperationManaged::class.java)

        val className = targetClass.simpleName
        val methodName = method.name

        val operation = methodAnnotation?.operation?.takeIf { it.isNotBlank() }
            ?: classAnnotation?.operation?.takeIf { it.isNotBlank() }
            ?: "$className#$methodName"

        val useCase = methodAnnotation?.useCase?.takeIf { it.isNotBlank() }
            ?: classAnnotation?.useCase?.takeIf { it.isNotBlank() }
            ?: "none"

        val event = methodAnnotation?.event?.takeIf { it.isNotBlank() }
            ?: classAnnotation?.event?.takeIf { it.isNotBlank() }
            ?: "none"

        val backup = mapOf(
            OperationMdcKeys.SERVICE to MDC.get(OperationMdcKeys.SERVICE),
            OperationMdcKeys.FUNCTION to MDC.get(OperationMdcKeys.FUNCTION),
            OperationMdcKeys.OPERATION to MDC.get(OperationMdcKeys.OPERATION),
            OperationMdcKeys.USE_CASE to MDC.get(OperationMdcKeys.USE_CASE),
            OperationMdcKeys.EVENT to MDC.get(OperationMdcKeys.EVENT)
        )

        try {
            MDC.put(OperationMdcKeys.SERVICE, className)
            MDC.put(OperationMdcKeys.FUNCTION, methodName)
            MDC.put(OperationMdcKeys.OPERATION, operation)
            MDC.put(OperationMdcKeys.USE_CASE, useCase)
            MDC.put(OperationMdcKeys.EVENT, event)

            return joinPoint.proceed()
        } finally {
            backup.forEach { (k, v) -> restore(k, v) }
        }
    }

    private fun restore(key: String, prev: String?) {
        if (prev == null) MDC.remove(key) else MDC.put(key, prev)
    }

}