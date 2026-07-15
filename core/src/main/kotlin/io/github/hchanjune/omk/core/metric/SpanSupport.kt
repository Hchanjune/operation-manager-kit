package io.github.hchanjune.omk.core.metric

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.provider.SpanIdProvider

/**
 * Builds and pushes the standard span shapes shared by the servlet and reactive aspects,
 * so span names, tags, and layers stay identical across both stacks by construction.
 */
object SpanSupport {

    fun pushEntrySpan(
        context: ManagedContext,
        className: String,
        methodName: String,
        idProvider: SpanIdProvider
    ): MetricSpan = context.push(
        name = MetricName("$className.$methodName"),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.Builder()
            .put("entrypoint", className)
            .put("method", methodName)
            .build(),
        descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
        idProvider = idProvider
    )

    fun pushOperationSpan(
        context: ManagedContext,
        operation: String,
        useCase: String,
        spanName: String,
        idProvider: SpanIdProvider
    ): MetricSpan = context.push(
        name = MetricName(spanName),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.Builder()
            .put("service", context.service)
            .put("operation", operation)
            .put("use_case", useCase)
            .build(),
        descriptor = MetricDescriptor(
            operation = operation,
            useCase = useCase,
            layer = MetricLayer.APPLICATION
        ),
        idProvider = idProvider
    )

    fun pushMetricSpan(
        context: ManagedContext,
        spanName: String,
        idProvider: SpanIdProvider
    ): MetricSpan = context.push(
        name = MetricName(spanName),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.Builder()
            .put("service", context.service)
            .put("operation", context.operation)
            .put("span", spanName)
            .build(),
        descriptor = MetricDescriptor(
            operation = context.operation,
            useCase = context.useCase,
            layer = MetricLayer.APPLICATION
        ),
        idProvider = idProvider
    )

    fun pushRepositorySpan(
        context: ManagedContext,
        className: String,
        methodName: String,
        idProvider: SpanIdProvider
    ): MetricSpan = context.push(
        name = MetricName("$className.$methodName"),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.Builder()
            .put("repository", className)
            .put("method", methodName)
            .put("operation", context.operation)
            .build(),
        descriptor = MetricDescriptor(
            operation = context.operation,
            useCase = context.useCase,
            layer = MetricLayer.DB
        ),
        idProvider = idProvider
    )

    fun pushCacheSpan(
        context: ManagedContext,
        className: String,
        methodName: String,
        idProvider: SpanIdProvider
    ): MetricSpan = context.push(
        name = MetricName("$className.$methodName"),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.Builder()
            .put("cache", className)
            .put("method", methodName)
            .put("operation", context.operation)
            .build(),
        descriptor = MetricDescriptor(
            operation = context.operation,
            useCase = context.useCase,
            layer = MetricLayer.CACHE
        ),
        idProvider = idProvider
    )
}
