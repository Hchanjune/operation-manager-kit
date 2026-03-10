package io.github.hchanjune.omk.webmvc.aspect

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect

@Aspect
class ManagedServiceAspect {

    @Around(
        "(@within(io.github.hchanjune.omk.core.annotations.OperationManaged) || " +
                "@annotation(io.github.hchanjune.omk.core.annotations.OperationManaged)) && " +
                "execution(* *(..))"
    )fun injectService(joinPoint: ProceedingJoinPoint): Any? {


        return joinPoint.proceed()
    }

}