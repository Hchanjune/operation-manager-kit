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
        val signature = joinPoint.signature

        val serviceKey = "${signature.declaringType.simpleName}#${signature.name}"

        Operations.context.injectService(
            service = serviceKey
        )
    }

}