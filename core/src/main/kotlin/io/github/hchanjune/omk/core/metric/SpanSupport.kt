package io.github.hchanjune.omk.core.metric

import io.github.hchanjune.omk.core.contants.ManagedProtocolType
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

    /**
     * The entry span of an HTTP controller. Besides [pushEntrySpan] it puts the HTTP method and the
     * matched route template on the trace span (`http.request.method`, `http.route`, OTel semantic
     * names) — trace-only, so OMK's metric labels do not change.
     *
     * HTTP 컨트롤러의 진입 span. [pushEntrySpan] 에 더해 HTTP 메서드와 매칭된 경로 템플릿을 trace span 에
     * 단다(`http.request.method`, `http.route` — OTel 표준 이름). trace 전용이라 OMK 메트릭 라벨은 바뀌지 않는다.
     */
    fun pushControllerSpan(
        context: ManagedContext,
        className: String,
        methodName: String,
        idProvider: SpanIdProvider
    ): MetricSpan = pushEntrySpan(context, className, methodName, idProvider).also { span ->
        if (context.protocol != ManagedProtocolType.HTTP) return@also
        if (context.method.isNotBlank()) span.setTraceAttribute("http.request.method", context.method)
        context.httpRoute()?.let { span.setTraceAttribute("http.route", it) }
    }

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

    fun pushExternalSpan(
        context: ManagedContext,
        className: String,
        methodName: String,
        destination: String,
        route: String,
        idProvider: SpanIdProvider
    ): MetricSpan = context.push(
        name = MetricName("$className.$methodName"),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        // A blank route is dropped by the builder rather than tagged "unknown".
        tags = MetricTags.Builder()
            .put("destination", destination)
            .put("route", route)
            .put("method", methodName)
            .put("operation", context.operation)
            .build(),
        descriptor = MetricDescriptor(
            operation = context.operation,
            useCase = context.useCase,
            layer = MetricLayer.EXTERNAL
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
