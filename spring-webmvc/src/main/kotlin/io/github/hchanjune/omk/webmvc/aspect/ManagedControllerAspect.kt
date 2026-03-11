package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before

@Aspect
class ManagedControllerAspect {

    @Before("@within(managedController)")
    fun injectEntryPoint(joinPoint: JoinPoint, managedController: ManagedController) {
        val signature = joinPoint.signature

        val entrypointKey = "${signature.declaringType.simpleName}#${signature.name}"

        Operations.context.injectEntryPoint(
            entrypoint = entrypointKey
        )
    }

}