package io.github.hchanjune.omk.servlet.aspect

import io.github.hchanjune.omk.core.annotations.ManagedEventCausationId
import io.github.hchanjune.omk.core.annotations.ManagedEventHandler
import io.github.hchanjune.omk.core.annotations.ManagedEventIssuer
import io.github.hchanjune.omk.core.annotations.ManagedEventTraceId
import io.github.hchanjune.omk.core.annotations.ManagedEventType
import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.servlet.Operations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedEventHandlerAspect(
    private val spanIdProvider: SpanIdProvider,
    private val runtime: OperationRuntime? = null,
) {

    @Around("@annotation(managedEventHandler)")
    fun aroundEventHandler(
        joinPoint: ProceedingJoinPoint,
        managedEventHandler: ManagedEventHandler
    ): Any? {
        val contextOwner = !Operations.hasContext

        if (contextOwner) {
            Operations.initializeForEvent(extractMetadata(joinPoint.args), runtime)
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

    private enum class AnnotatedFieldRole { TRACE_ID, CAUSATION_ID, ISSUER, EVENT_TYPE }
    private data class AnnotatedField(val field: java.lang.reflect.Field, val role: AnnotatedFieldRole)
    private data class AnnotatedMethod(val method: java.lang.reflect.Method, val role: AnnotatedFieldRole)

    private val annotationFieldCache = java.util.concurrent.ConcurrentHashMap<Class<*>, List<AnnotatedField>>()
    private val annotationMethodCache = java.util.concurrent.ConcurrentHashMap<Class<*>, List<AnnotatedMethod>>()

    private fun resolveAnnotatedFields(cls: Class<*>): List<AnnotatedField> =
        annotationFieldCache.getOrPut(cls) {
            val result = mutableListOf<AnnotatedField>()
            var cur: Class<*>? = cls
            while (cur != null && cur != Any::class.java) {
                for (field in cur.declaredFields) {
                    val role = when {
                        field.isAnnotationPresent(ManagedEventTraceId::class.java)    -> AnnotatedFieldRole.TRACE_ID
                        field.isAnnotationPresent(ManagedEventCausationId::class.java) -> AnnotatedFieldRole.CAUSATION_ID
                        field.isAnnotationPresent(ManagedEventIssuer::class.java)     -> AnnotatedFieldRole.ISSUER
                        field.isAnnotationPresent(ManagedEventType::class.java)       -> AnnotatedFieldRole.EVENT_TYPE
                        else -> null
                    }
                    if (role != null) {
                        field.isAccessible = true
                        result.add(AnnotatedField(field, role))
                    }
                }
                cur = cur.superclass
            }
            result
        }

    private fun resolveAnnotatedMethods(cls: Class<*>): List<AnnotatedMethod> =
        annotationMethodCache.getOrPut(cls) {
            val result = mutableListOf<AnnotatedMethod>()
            val visited = mutableSetOf<Class<*>>()
            val queue = ArrayDeque<Class<*>>()
            var cur: Class<*>? = cls
            while (cur != null) { queue.addAll(cur.interfaces); cur = cur.superclass }
            while (queue.isNotEmpty()) {
                val iface = queue.removeFirst()
                if (!visited.add(iface)) continue
                queue.addAll(iface.interfaces)
                for (method in iface.declaredMethods) {
                    val role = when {
                        method.isAnnotationPresent(ManagedEventTraceId::class.java)    -> AnnotatedFieldRole.TRACE_ID
                        method.isAnnotationPresent(ManagedEventCausationId::class.java) -> AnnotatedFieldRole.CAUSATION_ID
                        method.isAnnotationPresent(ManagedEventIssuer::class.java)     -> AnnotatedFieldRole.ISSUER
                        method.isAnnotationPresent(ManagedEventType::class.java)       -> AnnotatedFieldRole.EVENT_TYPE
                        else -> null
                    }
                    if (role != null) result.add(AnnotatedMethod(method, role))
                }
            }
            result
        }

    private fun tryExtractFromAnnotations(arg: Any): EventMetadata? {
        return runCatching {
            val fields = resolveAnnotatedFields(arg::class.java)
            val methods = resolveAnnotatedMethods(arg::class.java)
            if (fields.isEmpty() && methods.isEmpty()) return@runCatching null

            var traceId: String? = null
            var causationId: String? = null
            var issuer: String? = null
            var eventType: String? = null

            for ((field, role) in fields) {
                when (role) {
                    AnnotatedFieldRole.TRACE_ID     -> traceId     = field.get(arg) as? String
                    AnnotatedFieldRole.CAUSATION_ID -> causationId = field.get(arg) as? String
                    AnnotatedFieldRole.ISSUER       -> issuer      = field.get(arg) as? String
                    AnnotatedFieldRole.EVENT_TYPE   -> eventType   = field.get(arg) as? String
                }
            }

            for ((method, role) in methods) {
                when (role) {
                    AnnotatedFieldRole.TRACE_ID     -> if (traceId == null)     traceId     = method.invoke(arg) as? String
                    AnnotatedFieldRole.CAUSATION_ID -> if (causationId == null) causationId = method.invoke(arg) as? String
                    AnnotatedFieldRole.ISSUER       -> if (issuer == null)      issuer      = method.invoke(arg) as? String
                    AnnotatedFieldRole.EVENT_TYPE   -> if (eventType == null)   eventType   = method.invoke(arg) as? String
                }
            }

            EventMetadata(traceId, causationId, issuer, eventType)
        }.getOrNull()
    }

    private data class ConsumerRecordMethods(
        val topic: java.lang.reflect.Method,
        val headers: java.lang.reflect.Method,
        val lastHeader: java.lang.reflect.Method,
        val headerValue: java.lang.reflect.Method,
    )
    private val consumerRecordMethodCache = java.util.concurrent.ConcurrentHashMap<Class<*>, ConsumerRecordMethods>()

    private fun tryExtractFromConsumerRecord(arg: Any): EventMetadata? {
        if (arg::class.qualifiedName != "org.apache.kafka.clients.consumer.ConsumerRecord") return null
        return runCatching {
            val cls = arg::class.java
            val methods = consumerRecordMethodCache.getOrPut(cls) {
                val topicMethod = cls.getMethod("topic")
                val headersMethod = cls.getMethod("headers")
                val headersClass = headersMethod.returnType
                val lastHeaderMethod = headersClass.getMethod("lastHeader", String::class.java)
                val headerClass = Class.forName("org.apache.kafka.common.header.Header")
                val headerValueMethod = headerClass.getMethod("value")
                ConsumerRecordMethods(topicMethod, headersMethod, lastHeaderMethod, headerValueMethod)
            }

            val topic = methods.topic.invoke(arg) as? String
            val headers = methods.headers.invoke(arg) ?: return@runCatching null

            fun readHeader(name: String): String? = runCatching {
                val h = methods.lastHeader.invoke(headers, name) ?: return@runCatching null
                String(methods.headerValue.invoke(h) as ByteArray)
            }.getOrNull()

            // W3C traceparent: 00-{traceId}-{causationId}-{flags}
            val traceparent = readHeader("traceparent")
            if (traceparent != null) {
                val parts = traceparent.split("-")
                if (parts.size >= 4) {
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

    private data class MessageMethods(
        val getHeaders: java.lang.reflect.Method,
        val get: java.lang.reflect.Method,
    )
    private val messageMethodCache = java.util.concurrent.ConcurrentHashMap<Class<*>, MessageMethods?>()

    private fun tryExtractFromMessage(arg: Any): EventMetadata? {
        val cls = arg::class.java
        if (cls.interfaces.none { it.name == "org.springframework.messaging.Message" }) return null
        return runCatching {
            val methods = messageMethodCache.getOrPut(cls) {
                runCatching {
                    val getHeadersMethod = cls.getMethod("getHeaders")
                    val headersClass = getHeadersMethod.returnType
                    val getMethod = headersClass.getMethod("get", Any::class.java)
                    MessageMethods(getHeadersMethod, getMethod)
                }.getOrNull()
            } ?: return@runCatching null
            val headers = methods.getHeaders.invoke(arg)
            val get = methods.get

            fun readHeader(vararg names: String): String? =
                names.firstNotNullOfOrNull { runCatching { get.invoke(headers, it) as? String }.getOrNull() }

            val traceparent = readHeader("traceparent")
            if (traceparent != null) {
                val parts = traceparent.split("-")
                if (parts.size >= 4) {
                    return@runCatching EventMetadata(
                        traceId = parts[1],
                        causationId = parts[2],
                        issuer = readHeader("X-Issuer", "issuer"),
                        eventType = readHeader("eventType", "event-type")
                    )
                }
            }

            EventMetadata(
                traceId = readHeader("X-Trace-Id", "traceId"),
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
