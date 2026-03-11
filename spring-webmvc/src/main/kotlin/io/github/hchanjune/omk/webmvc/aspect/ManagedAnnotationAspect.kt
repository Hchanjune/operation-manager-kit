package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.OperationManaged
import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.aop.support.AopUtils
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ManagedAnnotationAspect {

    companion object {
        private val ANNOTATION_NAME = OperationManaged::class.java.name
    }

    @Before(
        "(@within(io.github.hchanjune.omk.core.annotations.OperationManaged) || " +
                "@annotation(io.github.hchanjune.omk.core.annotations.OperationManaged)) && " +
                "execution(* *(..))"
    )
    fun captureService(joinPoint: ProceedingJoinPoint): Any? {
        val targetClass = AopUtils.getTargetClass(joinPoint.target ?: joinPoint.signature.declaringType)
        val signature = joinPoint.signature as MethodSignature
        val method = signature.method

        val methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, OperationManaged::class.java)
        val classAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, OperationManaged::class.java)

        val operation = methodAnnotation?.operation?.takeIf { it.isNotBlank() }
            ?: classAnnotation?.operation?.takeIf { it.isNotBlank() }
            ?: ""

        val useCase = methodAnnotation?.useCase?.takeIf { it.isNotBlank() }
            ?: classAnnotation?.useCase?.takeIf { it.isNotBlank() }
            ?: ""

        val event = methodAnnotation?.event?.takeIf { it.isNotBlank() }
            ?: classAnnotation?.event?.takeIf { it.isNotBlank() }
            ?: ""

        Operations.context.injectAnnotationInfo(
            operation = operation,
            useCase = useCase,
        )

        return joinPoint.proceed()
    }

}