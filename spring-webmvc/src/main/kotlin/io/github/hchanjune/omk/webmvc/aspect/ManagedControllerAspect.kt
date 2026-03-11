package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.annotation.Pointcut

@Aspect
class ManagedControllerAspect {

    @Pointcut("@within(org.springframework.stereotype.Controller) || @within(org.springframework.web.bind.annotation.RestController)")
    fun controllerMethods() {}

    @Before("controllerMethods()")
    fun injectEntryPoint(joinPoint: JoinPoint) {
        val signature = joinPoint.signature
        val entrypointKey = "${signature.declaringType.simpleName}#${signature.name}"

        Operations.context.injectEntryPoint(
            entrypoint = entrypointKey
        )
    }


}