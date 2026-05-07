package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before

@Aspect
class ManagedServiceAspect {

    @Before("@within(managedService)")
    fun injectService(joinPoint: JoinPoint, managedService: ManagedService) {
        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        if (context.service == className) return
        Operations.context.injectService(
            service = className
        )
    }

}