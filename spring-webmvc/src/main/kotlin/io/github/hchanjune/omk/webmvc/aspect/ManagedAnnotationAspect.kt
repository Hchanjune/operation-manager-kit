package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ManagedAnnotationAspect {

    @Before("@annotation(operationAnnotation)")
    fun injectOperationInfo(operationAnnotation: ManagedOperation) {
        Operations.context.injectAnnotationInfo(
            operation = operationAnnotation.operation,
            useCase = operationAnnotation.useCase
        )
    }

}