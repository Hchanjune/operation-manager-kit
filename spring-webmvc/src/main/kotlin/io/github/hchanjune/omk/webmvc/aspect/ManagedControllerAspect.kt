package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature

@Aspect
class ManagedControllerAspect {

    @Pointcut("@within(org.springframework.stereotype.Controller) || @within(org.springframework.web.bind.annotation.RestController)")
    fun controllerMethods() {}

    @Around("controllerMethods()")
    fun injectEntryPoint(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature
        val entrypointKey = "${signature.declaringType.simpleName}#${signature.name}"

        Operations.context.injectEntryPoint(
            entrypoint = entrypointKey
        )

        return joinPoint.proceed()
    }


}