package io.github.hchanjune.omk.reactive.aspect

import io.github.hchanjune.omk.core.OperationRuntime
import io.github.hchanjune.omk.core.annotations.ManagedEventCausationId
import io.github.hchanjune.omk.core.annotations.ManagedEventIssuer
import io.github.hchanjune.omk.core.annotations.ManagedEventTraceId
import io.github.hchanjune.omk.core.annotations.ManagedEventType
import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.core.event.EventMetadata
import io.github.hchanjune.omk.core.metric.MetricDescriptor
import io.github.hchanjune.omk.core.metric.MetricKind
import io.github.hchanjune.omk.core.metric.MetricLayer
import io.github.hchanjune.omk.core.metric.MetricName
import io.github.hchanjune.omk.core.metric.MetricPolicy
import io.github.hchanjune.omk.core.metric.MetricTags
import io.github.hchanjune.omk.core.provider.SpanIdProvider
import io.github.hchanjune.omk.reactive.ReactiveOperations
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import reactor.core.publisher.Mono
import reactor.util.context.Context

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedEventHandlerAspect(
    private val spanIdProvider: SpanIdProvider,
    private val runtime: OperationRuntime? = null,
) : ReactiveAspectSupport() {

    @Around("@annotation(io.github.hchanjune.omk.core.annotations.ManagedEventHandler)")
    fun aroundEventHandler(joinPoint: ProceedingJoinPoint): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        // ── Mono return type ──────────────────────────────────────────────────────
        if (isMono(joinPoint)) {
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val contextOwner = !reactorCtx.hasKey(ReactiveOperations.CONTEXT_KEY)
                if (contextOwner) {
                    val newContext = ReactiveOperations.initializeForEvent(extractMetadata(joinPoint.args), runtime)
                    newContext.injectEntryPoint(className)
                    val span = newContext.push(
                        name = MetricName("$className.$methodName"),
                        kind = MetricKind.TIMER,
                        policy = MetricPolicy.defaults(),
                        tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                        descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                        idProvider = spanIdProvider
                    )
                    mono
                        .doOnSuccess { span.end(); newContext.pop(); newContext.end(); ReactiveOperations.hookFor(newContext)?.onSuccess(newContext) }
                        .doOnError { e -> span.end(e); newContext.pop(); newContext.end(); ReactiveOperations.hookFor(newContext)?.onFailure(newContext, e) }
                        .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, newContext))
                } else {
                    val ctx = reactorCtx.get<ManagedContext>(ReactiveOperations.CONTEXT_KEY)
                    ctx.injectEntryPoint(className)
                    val span = ctx.push(
                        name = MetricName("$className.$methodName"),
                        kind = MetricKind.TIMER,
                        policy = MetricPolicy.defaults(),
                        tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                        descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                        idProvider = spanIdProvider
                    )
                    mono
                        .doOnSuccess { span.end(); ctx.pop() }
                        .doOnError { e -> span.end(e); ctx.pop() }
                }
            }
        }

        // ── synchronous / blocking target ─────────────────────────────────────────
        val newContext = ReactiveOperations.initializeForEvent(extractMetadata(joinPoint.args), runtime)
        newContext.injectEntryPoint(className)
        val span = newContext.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )
        eventHandlerContext.set(newContext)
        return try {
            val result = joinPoint.proceed()
            span.end(); newContext.pop(); newContext.end()
            ReactiveOperations.hookFor(newContext)?.onSuccess(newContext)
            result
        } catch (e: Throwable) {
            span.end(e); newContext.pop(); newContext.end()
            ReactiveOperations.hookFor(newContext)?.onFailure(newContext, e)
            throw e
        } finally {
            eventHandlerContext.remove()
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
            var traceId: String? = null; var causationId: String? = null
            var issuer: String? = null;  var eventType: String? = null
            for ((field, role) in fields) when (role) {
                AnnotatedFieldRole.TRACE_ID     -> traceId     = field.get(arg) as? String
                AnnotatedFieldRole.CAUSATION_ID -> causationId = field.get(arg) as? String
                AnnotatedFieldRole.ISSUER       -> issuer      = field.get(arg) as? String
                AnnotatedFieldRole.EVENT_TYPE   -> eventType   = field.get(arg) as? String
            }
            for ((method, role) in methods) when (role) {
                AnnotatedFieldRole.TRACE_ID     -> if (traceId == null)     traceId     = method.invoke(arg) as? String
                AnnotatedFieldRole.CAUSATION_ID -> if (causationId == null) causationId = method.invoke(arg) as? String
                AnnotatedFieldRole.ISSUER       -> if (issuer == null)      issuer      = method.invoke(arg) as? String
                AnnotatedFieldRole.EVENT_TYPE   -> if (eventType == null)   eventType   = method.invoke(arg) as? String
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
                ConsumerRecordMethods(topicMethod, headersMethod, lastHeaderMethod, headerClass.getMethod("value"))
            }
            val topic = methods.topic.invoke(arg) as? String
            val headers = methods.headers.invoke(arg) ?: return@runCatching null
            fun readHeader(name: String): String? = runCatching {
                val h = methods.lastHeader.invoke(headers, name) ?: return@runCatching null
                String(methods.headerValue.invoke(h) as ByteArray)
            }.getOrNull()
            val traceparent = readHeader("traceparent")
            if (traceparent != null) {
                val parts = traceparent.split("-")
                if (parts.size >= 4) return@runCatching EventMetadata(parts[1], parts[2], readHeader("X-Issuer"), topic)
            }
            EventMetadata(readHeader("X-Trace-Id"), readHeader("X-Causation-Id"), readHeader("X-Issuer"), topic)
        }.getOrNull()
    }

    private data class MessageMethods(val getHeaders: java.lang.reflect.Method, val get: java.lang.reflect.Method)
    private val messageMethodCache = java.util.concurrent.ConcurrentHashMap<Class<*>, MessageMethods?>()

    private fun tryExtractFromMessage(arg: Any): EventMetadata? {
        val cls = arg::class.java
        if (cls.interfaces.none { it.name == "org.springframework.messaging.Message" }) return null
        return runCatching {
            val methods = messageMethodCache.getOrPut(cls) {
                runCatching {
                    val getHeadersMethod = cls.getMethod("getHeaders")
                    MessageMethods(getHeadersMethod, getHeadersMethod.returnType.getMethod("get", Any::class.java))
                }.getOrNull()
            } ?: return@runCatching null
            val headers = methods.getHeaders.invoke(arg)
            fun readHeader(vararg names: String): String? =
                names.firstNotNullOfOrNull { runCatching { methods.get.invoke(headers, it) as? String }.getOrNull() }
            val traceparent = readHeader("traceparent")
            if (traceparent != null) {
                val parts = traceparent.split("-")
                if (parts.size >= 4) return@runCatching EventMetadata(parts[1], parts[2], readHeader("X-Issuer", "issuer"), readHeader("eventType", "event-type"))
            }
            EventMetadata(readHeader("X-Trace-Id", "traceId"), readHeader("X-Causation-Id", "causationId"), readHeader("X-Issuer", "issuer"), readHeader("eventType", "event-type"))
        }.getOrNull()
    }

    private fun tryExtractFromReflection(arg: Any): EventMetadata? {
        return runCatching {
            val cls = arg::class.java
            fun find(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
                runCatching { cls.getDeclaredField(name).also { it.isAccessible = true }.get(arg) as? String }.getOrNull()
                    ?: runCatching { cls.getMethod("get${name.replaceFirstChar { it.uppercase() }}").invoke(arg) as? String }.getOrNull()
            }
            val traceId = find("traceId")
            val causationId = find("causationId")
            if (traceId == null && causationId == null) return@runCatching null
            EventMetadata(traceId, causationId, find("issuer"), find("eventType", "type"))
        }.getOrNull()
    }
}
