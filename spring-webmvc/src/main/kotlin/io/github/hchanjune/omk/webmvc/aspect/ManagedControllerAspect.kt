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
        val signature = joinPoint.signature as MethodSignature
        val className = signature.declaringType.simpleName
        val methodName = signature.method.name

        Operations.context.injectEntryPoint(
            entrypoint = "$className#$methodName"
        )

        return joinPoint.proceed()
    }


}