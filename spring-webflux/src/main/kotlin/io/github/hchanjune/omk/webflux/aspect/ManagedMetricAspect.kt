package io.github.hchanjune.omk.webflux.aspect

import io.github.hchanjune.omk.core.annotations.ManagedMetric
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webflux.ReactiveOperations
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import reactor.core.publisher.Mono

@Aspect
class ManagedMetricAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@annotation(managedMetric)")
    suspend fun aroundMetric(
        joinPoint: ProceedingJoinPoint,
        managedMetric: ManagedMetric
    ): Any? {
        val spanName = managedMetric.name.ifBlank {
            "${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name.substringBefore('-')}"
        }

        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val ctx = reactorCtx.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY).orElse(null)
                    ?: return@transformDeferredContextual mono
                val span = ctx.push(
                    name = MetricName(spanName),
                    kind = MetricKind.TIMER,
                    policy = MetricPolicy.defaults(),
                    tags = MetricTags.Builder()
                        .put("service", ctx.service)
                        .put("operation", ctx.operation)
                        .put("span", spanName)
                        .build(),
                    descriptor = MetricDescriptor(
                        operation = ctx.operation,
                        useCase = ctx.useCase,
                        layer = MetricLayer.APPLICATION
                    ),
                    idProvider = spanIdProvider
                )
                mono.doOnSuccess { span.end(); ctx.pop() }
                    .doOnError { e -> span.end(e); ctx.pop() }
            }
        }

        val ctx = currentCoroutineContext()[ReactorContext]?.context
            ?.getOrEmpty<ManagedContext>(ReactiveOperations.CONTEXT_KEY)?.orElse(null)
            ?: return joinPoint.proceed()

        val span = ctx.push(
            name = MetricName(spanName),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder()
                .put("service", ctx.service)
                .put("operation", ctx.operation)
                .put("span", spanName)
                .build(),
            descriptor = MetricDescriptor(
                operation = ctx.operation,
                useCase = ctx.useCase,
                layer = MetricLayer.APPLICATION
            ),
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
