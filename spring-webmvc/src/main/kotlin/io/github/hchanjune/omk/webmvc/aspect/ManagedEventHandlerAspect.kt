package io.github.hchanjune.omk.webmvc.aspect

import io.github.hchanjune.omk.core.annotations.ManagedEventCausationId
import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.annotations.ManagedEventIssuer
import io.github.hchanjune.omk.core.annotations.ManagedEventTraceId
import io.github.hchanjune.omk.core.annotations.ManagedEventType
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.webmvc.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedEventHandlerAspect(
    private val spanIdProvider: SpanIdProvider
) {

    @Around("@annotation(managedEventHandler)")
    fun aroundEventHandler(
        joinPoint: ProceedingJoinPoint,
        managedEventHandler: ManagedEventHandler
    ): Any? {
        val contextOwner = !Operations.hasContext

        if (contextOwner) {
            Operations.initializeForEvent(extractMetadata(joinPoint.args))
        }

        val context = Operations.context
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        context.injectEntryPoint(className)

        val span = context.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder()
                .put("entrypoint", className)
                .put("method", methodName)
                .build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )

        return try {
            val result = joinPoint.proceed()
            span.end()
            context.pop()
            if (contextOwner) {
                Operations.complete()
                Operations.hook?.onSuccess(context)
            }
            result
        } catch (e: Throwable) {
            span.end(e)
            context.pop()
            if (contextOwner) {
                Operations.complete()
                Operations.hook?.onFailure(context, e)
            }
            throw e
        } finally {
            if (contextOwner) Operations.clear()
        }
    }

    private fun extractMetadata(args: Array<Any?>): EventMetadata {
        for (arg in args) {
            if (arg == null) continue
            val result = tryExtractFromAnnotations(arg)
                ?: tryExtractFromConsumerRecord(arg)
                ?: tryExtractFromMessage(arg)
                ?: tryExtractFromReflection(arg)
            if (result != null) return result
        }
        return EventMetadata(null, null, null, null)
    }

    private fun tryExtractFromAnnotations(arg: Any): EventMetadata? {
        return runCatching {
            var traceId: String? = null
            var causationId: String? = null
            var issuer: String? = null
            var eventType: String? = null
            var found = false

            var cls: Class<*>? = arg::class.java
            while (cls != null && cls != Any::class.java) {
                for (field in cls.declaredFields) {
                    field.isAccessible = true
                    when {
                        field.isAnnotationPresent(ManagedEventTraceId::class.java) -> {
                            traceId = field.get(arg) as? String
                            found = true
                        }
                        field.isAnnotationPresent(ManagedEventCausationId::class.java) -> {
                            causationId = field.get(arg) as? String
                            found = true
                        }
                        field.isAnnotationPresent(ManagedEventIssuer::class.java) -> {
                            issuer = field.get(arg) as? String
                            found = true
                        }
                        field.isAnnotationPresent(ManagedEventType::class.java) -> {
                            eventType = field.get(arg) as? String
                            found = true
                        }
                    }
                }
                cls = cls.superclass
            }

            if (!found) null
            else EventMetadata(traceId, causationId, issuer, eventType)
        }.getOrNull()
    }

    private fun tryExtractFromConsumerRecord(arg: Any): EventMetadata? {
        if (arg::class.qualifiedName != "org.apache.kafka.clients.consumer.ConsumerRecord") return null
        return runCatching {
            val cls = arg::class.java
            val topic = cls.getMethod("topic").invoke(arg) as? String
            val headers = cls.getMethod("headers").invoke(arg) ?: return@runCatching null
            val lastHeader = headers::class.java.getMethod("lastHeader", String::class.java)

            fun readHeader(name: String): String? = runCatching {
                val h = lastHeader.invoke(headers, name) ?: return@runCatching null
                String(h::class.java.getMethod("value").invoke(h) as ByteArray)
            }.getOrNull()

            // W3C traceparent: 00-{traceId}-{causationId}-{flags}
            val traceparent = readHeader("traceparent")
            if (traceparent != null) {
                val parts = traceparent.split("-")
                if (parts.size >= 3) {
                    return@runCatching EventMetadata(
                        traceId = parts[1],
                        causationId = parts[2],
                        issuer = readHeader("X-Issuer"),
                        eventType = topic
                    )
                }
            }

            EventMetadata(
                traceId = readHeader("X-Trace-Id"),
                causationId = readHeader("X-Causation-Id"),
                issuer = readHeader("X-Issuer"),
                eventType = topic
            )
        }.getOrNull()
    }

    private fun tryExtractFromMessage(arg: Any): EventMetadata? {
        if (arg::class.java.interfaces.none { it.name == "org.springframework.messaging.Message" }) return null
        return runCatching {
            val headers = arg::class.java.getMethod("getHeaders").invoke(arg)
            val get = headers::class.java.getMethod("get", Any::class.java)

            fun readHeader(vararg names: String): String? =
                names.firstNotNullOfOrNull { runCatching { get.invoke(headers, it) as? String }.getOrNull() }

            EventMetadata(
                traceId = readHeader("traceparent", "X-Trace-Id", "traceId"),
                causationId = readHeader("X-Causation-Id", "causationId"),
                issuer = readHeader("X-Issuer", "issuer"),
                eventType = readHeader("eventType", "event-type")
            )
        }.getOrNull()
    }

    private fun tryExtractFromReflection(arg: Any): EventMetadata? {
        return runCatching {
            val cls = arg::class.java

            fun find(vararg names: String): String? =
                names.firstNotNullOfOrNull { name ->
                    runCatching {
                        cls.getDeclaredField(name).also { it.isAccessible = true }.get(arg) as? String
                    }.getOrNull() ?: runCatching {
                        cls.getMethod("get${name.replaceFirstChar { it.uppercase() }}").invoke(arg) as? String
                    }.getOrNull()
                }

            val traceId = find("traceId")
            val causationId = find("causationId")
            if (traceId == null && causationId == null) return@runCatching null

            EventMetadata(
                traceId = traceId,
                causationId = causationId,
                issuer = find("issuer"),
                eventType = find("eventType", "type")
            )
        }.getOrNull()
    }
}
