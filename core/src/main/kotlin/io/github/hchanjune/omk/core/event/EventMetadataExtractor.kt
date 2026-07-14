package io.github.hchanjune.omk.core.event

import io.github.hchanjune.omk.core.annotations.ManagedEventCausationId
import io.github.hchanjune.omk.core.annotations.ManagedEventIssuer
import io.github.hchanjune.omk.core.annotations.ManagedEventTraceId
import io.github.hchanjune.omk.core.annotations.ManagedEventType
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Extracts [EventMetadata] from event-handler method arguments, trying in order:
 * OMK field/getter annotations → Kafka ConsumerRecord headers → Spring Messaging headers →
 * conventional field names by reflection. All lookups are reflection-based (no compile-time
 * dependency on Kafka or Spring Messaging) and cached per class.
 *
 * Shared by the servlet and reactive @ManagedEventHandler aspects.
 */
object EventMetadataExtractor {

    fun extract(args: Array<Any?>): EventMetadata {
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
    private data class AnnotatedField(val field: Field, val role: AnnotatedFieldRole)
    private data class AnnotatedMethod(val method: Method, val role: AnnotatedFieldRole)

    private val annotationFieldCache = ConcurrentHashMap<Class<*>, List<AnnotatedField>>()
    private val annotationMethodCache = ConcurrentHashMap<Class<*>, List<AnnotatedMethod>>()

    private fun resolveAnnotatedFields(cls: Class<*>): List<AnnotatedField> =
        annotationFieldCache.getOrPut(cls) {
            val result = mutableListOf<AnnotatedField>()
            var cur: Class<*>? = cls
            while (cur != null && cur != Any::class.java) {
                for (field in cur.declaredFields) {
                    val role = when {
                        field.isAnnotationPresent(ManagedEventTraceId::class.java)     -> AnnotatedFieldRole.TRACE_ID
                        field.isAnnotationPresent(ManagedEventCausationId::class.java) -> AnnotatedFieldRole.CAUSATION_ID
                        field.isAnnotationPresent(ManagedEventIssuer::class.java)      -> AnnotatedFieldRole.ISSUER
                        field.isAnnotationPresent(ManagedEventType::class.java)        -> AnnotatedFieldRole.EVENT_TYPE
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
                        method.isAnnotationPresent(ManagedEventTraceId::class.java)     -> AnnotatedFieldRole.TRACE_ID
                        method.isAnnotationPresent(ManagedEventCausationId::class.java) -> AnnotatedFieldRole.CAUSATION_ID
                        method.isAnnotationPresent(ManagedEventIssuer::class.java)      -> AnnotatedFieldRole.ISSUER
                        method.isAnnotationPresent(ManagedEventType::class.java)        -> AnnotatedFieldRole.EVENT_TYPE
                        else -> null
                    }
                    if (role != null) {
                        method.isAccessible = true
                        result.add(AnnotatedMethod(method, role))
                    }
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
        val topic: Method,
        val headers: Method,
        val lastHeader: Method,
        val headerValue: Method,
    )
    private val consumerRecordMethodCache = ConcurrentHashMap<Class<*>, ConsumerRecordMethods>()

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
        val getHeaders: Method,
        val get: Method,
    )
    private val messageMethodCache = ConcurrentHashMap<Class<*>, MessageMethods?>()

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
                        // setAccessible: the getter may sit on a package-private/internal event
                        // class, which is not invokable across packages otherwise.
                        cls.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                            .also { it.isAccessible = true }
                            .invoke(arg) as? String
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
