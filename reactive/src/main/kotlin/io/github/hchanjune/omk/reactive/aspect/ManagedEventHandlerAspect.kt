package io.github.hchanjune.omk.reactive.aspect

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import reactor.core.publisher.Mono
import reactor.util.context.Context
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class ManagedEventHandlerAspect(
    private val spanIdProvider: SpanIdProvider
) : ReactiveAspectSupport() {

    @Around("@annotation(io.github.hchanjune.omk.core.annotations.ManagedEventHandler)")
    fun aroundEventHandler(joinPoint: ProceedingJoinPoint): Any? {
        val className = joinPoint.signature.declaringType.simpleName
        val methodName = joinPoint.signature.name.substringBefore('-')

        println("[OMK-DEBUG] aroundEventHandler: $className.$methodName | isMono=${isMono(joinPoint)} | lastArg=${joinPoint.args.lastOrNull()?.let { it::class.simpleName } ?: "null"}")

        // ── Mono return type ──────────────────────────────────────────────────────
        if (isMono(joinPoint)) {
            println("[OMK-DEBUG] -> isMono path")
            val result = joinPoint.proceed() as Mono<*>
            return result.transformDeferredContextual { mono, reactorCtx ->
                val contextOwner = !reactorCtx.hasKey(ReactiveOperations.CONTEXT_KEY)
                println("[OMK-DEBUG] isMono subscribed: owner=$contextOwner hook=${ReactiveOperations.hook != null}")
                if (contextOwner) {
                    val newContext = ReactiveOperations.initializeForEvent(extractMetadata(joinPoint.args))
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
                        .doOnSuccess {
                            println("[OMK-DEBUG] isMono.doOnSuccess (owner)")
                            span.end(); newContext.pop(); newContext.end()
                            ReactiveOperations.hook?.onSuccess(newContext)
                        }
                        .doOnError { e ->
                            println("[OMK-DEBUG] isMono.doOnError (owner): ${e.message}")
                            span.end(e); newContext.pop(); newContext.end()
                            ReactiveOperations.hook?.onFailure(newContext, e)
                        }
                        .doFinally { sig -> println("[OMK-DEBUG] isMono.terminal (owner): $sig") }
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
                        .doOnSuccess { println("[OMK-DEBUG] isMono.doOnSuccess (nonOwner)"); span.end(); ctx.pop() }
                        .doOnError { e -> println("[OMK-DEBUG] isMono.doOnError (nonOwner): ${e.message}"); span.end(e); ctx.pop() }
                        .doFinally { sig -> println("[OMK-DEBUG] isMono.terminal (nonOwner): $sig") }
                }
            }
        }

        // ── suspend fun target — bridge via original Continuation ─────────────────
        val paramTypes = (joinPoint.signature as MethodSignature).method.parameterTypes
        val isSuspendTarget = paramTypes.isNotEmpty() &&
            Continuation::class.java.isAssignableFrom(paramTypes.last())

        if (isSuspendTarget) {
            @Suppress("UNCHECKED_CAST")
            val cont = joinPoint.args.last() as Continuation<Any?>
            val existingCtx = getManagedContext(joinPoint)
            println("[OMK-DEBUG] -> suspend path | hasExistingCtx=${existingCtx != null} | hook=${ReactiveOperations.hook?.let { it::class.simpleName } ?: "NULL"}")

            val instrumented: Mono<*>
            if (existingCtx != null) {
                existingCtx.injectEntryPoint(className)
                val span = existingCtx.push(
                    name = MetricName("$className.$methodName"),
                    kind = MetricKind.TIMER,
                    policy = MetricPolicy.defaults(),
                    tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                    descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                    idProvider = spanIdProvider
                )
                instrumented = proceedAsMono(joinPoint)
                    .doOnSuccess { span.end(); existingCtx.pop() }
                    .doOnError { e -> span.end(e); existingCtx.pop() }
                    .doFinally { sig -> println("[OMK-DEBUG] suspend.terminal (nonOwner): $sig") }
            } else {
                val newCtx = ReactiveOperations.initializeForEvent(extractMetadata(joinPoint.args))
                newCtx.injectEntryPoint(className)
                val span = newCtx.push(
                    name = MetricName("$className.$methodName"),
                    kind = MetricKind.TIMER,
                    policy = MetricPolicy.defaults(),
                    tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                    descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                    idProvider = spanIdProvider
                )
                instrumented = proceedAsMono(joinPoint)
                    .doOnSuccess {
                        println("[OMK-DEBUG] suspend.doOnSuccess (owner)")
                        span.end(); newCtx.pop(); newCtx.end()
                        ReactiveOperations.hook?.onSuccess(newCtx)
                    }
                    .doOnError { e ->
                        println("[OMK-DEBUG] suspend.doOnError (owner): ${e.message}")
                        span.end(e); newCtx.pop(); newCtx.end()
                        ReactiveOperations.hook?.onFailure(newCtx, e)
                    }
                    .doFinally { sig -> println("[OMK-DEBUG] suspend.terminal (owner): $sig") }
                    .contextWrite(Context.of(ReactiveOperations.CONTEXT_KEY, newCtx))
            }

            CoroutineScope(cont.context).launch {
                try {
                    cont.resume(instrumented.awaitSingleOrNull())
                } catch (e: Throwable) {
                    cont.resumeWithException(e)
                }
            }
            return COROUTINE_SUSPENDED
        }

        // ── plain synchronous target ──────────────────────────────────────────────
        val existingCtx = getManagedContext(joinPoint)
        println("[OMK-DEBUG] -> sync path | hasExistingCtx=${existingCtx != null}")

        if (existingCtx != null) {
            existingCtx.injectEntryPoint(className)
            val span = existingCtx.push(
                name = MetricName("$className.$methodName"),
                kind = MetricKind.TIMER,
                policy = MetricPolicy.defaults(),
                tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
                descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
                idProvider = spanIdProvider
            )
            val result = try {
                joinPoint.proceed()
            } catch (e: Throwable) {
                span.end(e); existingCtx.pop(); throw e
            }
            return if (result is Mono<*>) {
                result.doOnSuccess { span.end(); existingCtx.pop() }.doOnError { e -> span.end(e); existingCtx.pop() }
            } else {
                span.end(); existingCtx.pop(); result
            }
        }

        val newContext = ReactiveOperations.initializeForEvent(extractMetadata(joinPoint.args))
        newContext.injectEntryPoint(className)
        val span = newContext.push(
            name = MetricName("$className.$methodName"),
            kind = MetricKind.TIMER,
            policy = MetricPolicy.defaults(),
            tags = MetricTags.Builder().put("entrypoint", className).put("method", methodName).build(),
            descriptor = MetricDescriptor(layer = MetricLayer.ENTRY),
            idProvider = spanIdProvider
        )
        return try {
            val result = joinPoint.proceed()
            span.end(); newContext.pop(); newContext.end()
            ReactiveOperations.hook?.onSuccess(newContext)
            result
        } catch (e: Throwable) {
            span.end(e); newContext.pop(); newContext.end()
            ReactiveOperations.hook?.onFailure(newContext, e)
            throw e
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
