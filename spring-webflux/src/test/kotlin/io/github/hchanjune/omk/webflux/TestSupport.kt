package io.github.hchanjune.omk.webflux

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricSpan
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import java.util.concurrent.atomic.AtomicInteger

internal object TestSupport {

    private val counter = AtomicInteger()
    val spanIdProvider = SpanIdProvider { "test-span-${counter.incrementAndGet()}" }

    fun context(
        traceId: String = "",
        causationId: String = ""
    ): ManagedContext = ManagedContext(spanIdProvider = spanIdProvider).also {
        it.injectTraceId(traceId)
        it.injectCausationId(causationId)
        it.start()
    }

    fun ManagedContext.pushSpan(
        name: String,
        layer: MetricLayer = MetricLayer.APPLICATION
    ): MetricSpan = push(
        name = MetricName(name),
        kind = MetricKind.TIMER,
        policy = MetricPolicy.defaults(),
        tags = MetricTags.empty(),
        descriptor = MetricDescriptor(layer = layer),
        idProvider = spanIdProvider
    )

    fun ManagedContext.buildTree(): Triple<MetricSpan, MetricSpan, MetricSpan> {
        val root    = pushSpan("EntryController", MetricLayer.ENTRY)
        val service = pushSpan("CreateOrder",     MetricLayer.APPLICATION)
        val db      = pushSpan("OrderRepository.save", MetricLayer.DB)
        db.end();      pop()
        service.end(); pop()
        root.end();    pop()
        return Triple(root, service, db)
    }
}
