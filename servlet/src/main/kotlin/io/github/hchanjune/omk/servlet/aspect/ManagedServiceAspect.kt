package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.servlet.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class ManagedServiceAspect {

    @Around("@within(managedService)")
    fun injectService(
        joinPoint: ProceedingJoinPoint,
        managedService: ManagedService
    ): Any? {
        if (!Operations.hasContext) return joinPoint.proceed()
        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        if (context.service != className) context.injectService(className)
        return joinPoint.proceed()
    }

}