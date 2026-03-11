package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut

@Aspect
class ManagedServiceAspect {

    @Pointcut("@within(org.springframework.stereotype.Service)")
    fun serviceMethods() {}

    @Before("serviceMethods()")
    fun injectService(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature
        val serviceKey = "${signature.declaringType.simpleName}#${signature.name}"

        Operations.context.injectService(
            service = serviceKey
        )

        return joinPoint.proceed()
    }

}