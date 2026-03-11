package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.ManagedRepository
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before

@Aspect
class ManagedRepositoryAspect {

    @Before("@within(managedRepository)")
    fun injectRepositoryInfo(joinPoint: JoinPoint, managedRepository: ManagedRepository) {
        val signature = joinPoint.signature

    }

}