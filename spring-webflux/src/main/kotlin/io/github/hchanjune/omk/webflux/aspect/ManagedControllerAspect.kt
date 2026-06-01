package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webflux.ReactiveOperations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import reactor.core.publisher.Mono

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedControllerAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@within(managedController)")
    fun aroundController(
        joinPoint: ProceedingJoinPoint,
        managedController: ManagedController
    ): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val ctx = reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)
                    ?: return@transformDeferredContextual mono
                if (ctx.entrypoint != className) ctx.injectEntryPoint(className)
                val span = ctx.push(
                    name = MetricName("$className.$methodName"),
                    kind = MetricKind.TIMER,
                    policy = MetricPolicy.defaults(),
                    tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                    descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                    idProvider = spanIdProvider
                )
                mono.doOnSuccess { span.end(); ctx.pop() }
                    .doOnError { e -> span.end(e); ctx.pop() }
            }
        }

        val ctx = getManagedContext(joinPoint) ?: return joinPoint.proceed()

        if (ctx.entrypoint != className) ctx.injectEntryPoint(className)
        val span = ctx.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )

        return try {
            val result = joinPoint.proceed()
            span.end(); ctx.pop()
            result
        } catch (e: Throwable) {
            span.end(e); ctx.pop()
            throw e
        }
    }
}
