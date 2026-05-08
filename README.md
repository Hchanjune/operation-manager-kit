# Operation Manager Kit

[![JitPack](https://jitpack.io/v/Hchanjune/operation-manager-kit.svg)](https://jitpack.io/#Hchanjune/operation-manager-kit)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen)
![Java](https://img.shields.io/badge/Java-21-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple)

**[한국어](README.ko.md) | English**

---

**Operation Manager Kit** is a lightweight observability and distributed tracing library for Spring-based applications.

It provides a structured execution boundary around business logic, automatically capturing consistent metadata such as trace IDs, issuer identity, HTTP context, service/operation names, execution timing, and lifecycle hooks — with minimal configuration.

---

## Modules

| Module | Description |
|--------|-------------|
| `core` | Framework-agnostic execution engine, context model, and provider contracts |
| `spring-webmvc` | Spring Boot auto-configuration with AOP aspects, servlet filters, and Micrometer integration |

---

## Installation

Add JitPack to your repositories and include the desired modules.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:core:x.x.x")
    implementation("com.github.Hchanjune.operation-manager-kit:spring-webmvc:x.x.x")
}
```

---

## Quick Start

### 1. Annotate your components

The recommended pattern is to place `@ManagedOperation` on the primary business logic handler in your service and return the result of `Operations { }` directly. Kotlin's type inference resolves the return type automatically, or you can declare it explicitly.

```kotlin
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.webmvc.Operations

@ManagedController
@RestController
class OrderController(private val orderService: OrderService) {

    @PostMapping("/orders")
    fun create(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        val result = orderService.create(request)
        return ResponseEntity.ok(result.data)
    }
}

@ManagedService
@Service
class OrderService(private val orderRepository: OrderRepository) {

    // Return type inferred as OperationResult<OrderResponse>
    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        val order = orderRepository.save(Order.from(request))
        OrderResponse.from(order)
    }

    // Explicit return type is also valid
    @ManagedOperation(operation = "CancelOrder", useCase = "CancelOrder")
    fun cancel(orderId: Long): OperationResult<Unit> = Operations {
        orderRepository.cancel(orderId)
    }
}

@ManagedRepository
@Repository
class OrderRepository {
    fun save(order: Order): Order { ... }
    fun cancel(orderId: Long) { ... }
}
```

### 2. Access context via Operations

`Operations.context` is accessible globally from anywhere within the managed scope — not just inside an `Operations { }` block. All code executing within the same managed trigger (HTTP request, Kafka message, gRPC call, or any future protocol) shares the same `ManagedContext` instance.

```kotlin
@Service
class AuditService {
    fun record() {
        val ctx = Operations.context  // accessible anywhere in the managed scope
        println(ctx.traceId)
        println(ctx.issuer)
    }
}
```

Context state is cumulative and reflects the lifecycle at the point of access. Values injected by annotations (`@ManagedController`, `@ManagedService`, `@ManagedOperation`) become available as each layer is entered. Accessing `Operations.context` before an annotation has been processed means that field is not yet populated.

```kotlin
// At entry point  → traceId ✓  issuer ✓  service ✗  operation ✗
// In service      → traceId ✓  issuer ✓  service ✓  operation ✗
// In @ManagedOperation → all fields ✓
```

The `Operations { }` block additionally captures the result alongside the context:

```kotlin
val result = Operations { // this: ManagedContext
    println(traceId)
    println(issuer)
    println(durationMs)
    "OK"
}

println(result.context.traceId)
println(result.data) // "OK"
```

---

## Annotations

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@ManagedController` | Class | Opens an ENTRY-layer root span per handler method; injects entrypoint into context |
| `@ManagedService` | Class | Injects service name into context |
| `@ManagedRepository` | Class | Instruments all methods on a repository as DB-layer child spans |
| `@ManagedOperation` | Method | Injects `operation` and `useCase` into context; opens an APPLICATION-layer span |
| `@ManagedMetric` | Method | Instruments any method as a named APPLICATION-layer child span |
| `@ManagedEventHandler` | Method | Opens an ENTRY-layer span for messaging handlers; auto-extracts trace context from event arguments; sets `executionScope` to `EVENT` |

> `@ManagedController` creates one span per handler method invocation — it is the designated root of the span tree for HTTP requests. `@ManagedEventHandler` serves the same role for messaging-based entry points (Kafka, RabbitMQ, etc.). `@ManagedMetric` defaults to `ClassName.methodName` and can be overridden with the `name` attribute — keep it low-cardinality to avoid metric tag explosion.

---

## Hook System

Hooks allow you to react to operation lifecycle events (success or failure).

### Hook Ordering

Hooks are ordered using Spring's `@Order` annotation. The built-in `DefaultOperationLoggingHook` is registered at **Order 50**, which serves as the reference point.

```
Order < 50  →  Pre-logging hooks      (context enrichment, preprocessing)
Order 50    →  DefaultOperationLoggingHook  (logs the fully enriched context)
Order 60    →  MetricsOperationHook   (records all span metrics to Micrometer)
Order > 60  →  Custom post-logging hooks   (notifications, alerting, etc.)
```

Hooks registered **before** the logging hook (Order < 50) will have their results included in the log output.

### Hook Results in Context

Each hook's execution result is recorded in `ManagedContext.hookRecords` after it runs.

```kotlin
context.hookRecords.forEach { record ->
    println("${record.hookName}: ${if (record.success) "OK" else "FAIL"}")
}
```

If any hook throws an exception, it is **isolated** — subsequent hooks continue to run. The `CompositeOperationHook` logs a warning for each failure.

### Custom Hook Example

```kotlin
@Component
@Order(30) // runs before DefaultOperationLoggingHook
class MyEnrichmentHook : OperationHook {

    override fun onSuccess(context: ManagedContext) {
        context.message = "Processed successfully"
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        context.message = "Failed: ${exception.message}"
    }
}
```

### Default Logging Hook

The built-in `DefaultOperationLoggingHook` supports both pretty-print and JSON log formats.

**Options:**

| Property | Default | Description |
|----------|---------|-------------|
| `pretty` | `false` | Human-readable formatted output |
| `json` | `true` | JSON format, recommended for production |
| `spans` | `false` | Append span tree to pretty output |
| `success-level` | `INFO` | Log level for successful operations |
| `failure-level` | `ERROR` | Log level for failed operations |

**Pretty output (with `spans: true`):**
```
┌───────────────────────────────────────────────────────────────────────────────────
│ ✅ Success
├─ Status      : SUCCESS
├─ TraceId     : 4bf92f3577b34da6a3ce929d0e0e4736
├─ CausationId : 00f067aa0ba902b7
├─ Issuer      : john.doe
├─ Protocol    : HTTP
├─ Type        : REST
├─ HTTP_URI    : /orders
├─ HTTP_METHOD : POST
├─ Entry Point : OrderController
├─ Service     : OrderService
├─ Operation   : CreateOrder
├─ UseCase     : PlaceOrder
├─ Message     :
├─ Response    :
├─ Performance : 56Ms
├─ Timestamp   : 2025-05-07T12:34:56.733Z
├─ Hooks       : MyEnrichmentHook=OK
├─ Spans      :
│    [ENT] 12:34:56.733  [nio-8080-exec-1]  OrderController.create        [56ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [nio-8080-exec-1]  CreateOrder           [48ms]   SUCCESS
│                   └─ [APP] 12:34:56.742  [nio-8080-exec-1]  validateInventory  [9ms]   SUCCESS
│                   └─ [DB ] 12:34:56.751  [nio-8080-exec-1]  OrderRepository.save  [18ms]  SUCCESS
│                   └─ [DB ] 12:34:56.769  [nio-8080-exec-1]  OrderRepository.findByCustomerId  [7ms]  SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

---

## Span Metrics

Every annotated method boundary is captured as a **metric span** — a timed unit of work with outcome, tags, and nesting. Spans form a tree rooted at the entry point.

### Span Hierarchy

```
@ManagedController    ──  [ENT] root span  (HTTP entry point, per handler method)
@ManagedEventHandler  ──  [ENT] root span  (messaging entry point, executionScope = EVENT)
    └── @ManagedOperation  ──  [APP] child span  (operation + useCase tags)
            └── @ManagedMetric      ──  [APP] child span  (any method)
            └── @ManagedRepository  ──  [DB]  child span  (per repository method)
```

If neither `@ManagedController` nor `@ManagedEventHandler` is present (e.g. a direct service call), `@ManagedOperation` becomes the root span automatically.

### Example

```kotlin
@ManagedController
@RestController
class OrderController(private val orderService: OrderService) {

    @PostMapping("/orders")
    fun create(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        val result = orderService.create(request)   // [ENT] span wraps everything below
        return ResponseEntity.ok(result.data)
    }
}

@ManagedService
@Service
class OrderService(private val repo: OrderRepository) {

    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        validateInventory(request)          // [APP] span via @ManagedMetric
        repo.save(Order.from(request))      // [DB]  span via @ManagedRepository
    }

    @ManagedMetric(name = "validateInventory")
    private fun validateInventory(request: OrderRequest) { ... }
}

@ManagedRepository
@Repository
class OrderRepository {
    fun save(order: Order): Order { ... }
}
```

Spans are collected during the request and flushed by `MetricsOperationHook` (Order 60) when the request completes. Each span is recorded to Micrometer as `omk.span.duration` (timer) or `omk.span.count` (counter) with the following tags:

| Tag | Source | Layer |
|-----|--------|-------|
| `entrypoint` | Controller class name | ENT |
| `method` | Controller method name | ENT |
| `service` | `@ManagedService` class name | APP |
| `operation` | `@ManagedOperation.operation` | APP |
| `use_case` | `@ManagedOperation.useCase` | APP |
| `span` | Span name | APP (`@ManagedMetric`) |
| `repository` | Repository class name | DB |
| `method` | Repository method name | DB |
| `status` | `success` or `failure` | all |
| `error_type` | Exception class name (on failure) | all |

---

## Distributed Tracing

The library propagates trace context across service boundaries using the **W3C Trace Context** standard by default.

### W3C Standard Mode (default)

Incoming `traceparent` header is parsed and the response includes the updated `traceparent`.

```
traceparent: 00-{traceId 32hex}-{spanId 16hex}-01
```

The response `traceparent` is injected **after** request processing completes, so it always reflects the actual root span ID.

### Custom Header Mode

```yaml
operation-manager:
  webmvc:
    telemetry:
      propagation:
        mode: CUSTOM
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## Configuration Properties

All properties are under the `operation-manager.webmvc` prefix.

```yaml
operation-manager:
  webmvc:
    context-filter:
      enabled: true       # Enable/disable the servlet filter (default: true)

    context-aspect:
      enabled: true       # Enable/disable AOP aspects (default: true)

    micrometer:
      enabled: true       # Enable/disable Micrometer metrics recording (default: true)

    logging:
      enabled: true
      pretty: false       # Pretty-print format (human-readable)
      json: true          # JSON format (recommended for production)
      spans: false        # Append span tree to pretty output (default: false)
      success-level: INFO
      failure-level: ERROR

    async-propagation:
      enabled: true       # Enable/disable @Async context propagation (default: true)
      hook-enabled: false # Execute hooks when async task/coroutine completes (default: false)

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        generate-when-missing: true  # generate traceId/causationId if not in incoming headers (default: true)
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## `@Async` Context Propagation

When `@EnableAsync` is active, `ManagedContextTaskDecorator` is automatically registered and applied to the default `ThreadPoolTaskExecutor`. When an `@Async` method is called, the context is **forked** — the async thread receives an independent copy, not a reference to the same object.

### What is propagated (forked)

| Field | Behavior |
|-------|----------|
| `traceId`, `causationId`, `issuer` | Inherited — for log correlation across threads |
| `protocol`, `type`, `uri`, `method`, `entrypoint`, `service`, `operation`, `useCase` | Inherited — for consistent tagging |
| `executionScope` | Set to `ASYNC` in the forked context |
| Span tree, timing, hook records | **Independent** — not shared with the parent |

### Async span tree

When the async thread starts, an `async.execution` root span is automatically created. Any `@ManagedOperation`, `@ManagedMetric`, or `@ManagedRepository` calls inside the async method become child spans of this root — forming an independent span tree separate from the main request.

```
Main thread span tree:          Async thread span tree:
[ENT] OrderController           [ENT] async.execution
  └─ [APP] CreateOrder            └─ [APP] SendNotification
       └─ [DB] save                    └─ [DB] logRepository.insert
```

### Hook execution

By default, hooks do **not** run in async contexts. You can enable them globally via configuration or per-request in business logic.

**Global default:**

```yaml
operation-manager:
  webmvc:
    async-propagation:
      enabled: true
      hook-enabled: true  # run hooks when async task completes (default: false)
```

**Per-request control:**

The `isAsyncHookEnabled` flag is inherited when the context is forked. Toggle it before the async call to control which tasks get hook execution:

```kotlin
// Enable hooks for async tasks in this request
Operations.context.enableAsyncHook()
asyncService.doTrackedWork()  // hooks fire on completion

// Disable for fire-and-forget tasks
Operations.context.disableAsyncHook()
asyncService.fireAndForget()  // no hooks
```

> Hooks fire on the async thread when the task completes, independently of the main thread. Async metrics are recorded separately and do not appear in the main request's span tree.

To disable context propagation entirely:

```yaml
operation-manager:
  webmvc:
    async-propagation:
      enabled: false
```

> If you have a custom `AsyncConfigurer` or `ThreadPoolTaskExecutor`, inject `ManagedContextTaskDecorator` and apply it manually.

---

## Kotlin Coroutine Context Propagation

When `kotlinx-coroutines-core` is on the classpath, `ManagedContextElement` propagates the managed context across suspension points and thread switches.

`ManagedContextElement` implements `CopyableThreadContextElement`. When a child coroutine is launched, `copyForChild()` is called automatically — each child receives its own **forked** context with an independent span tree.

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    // Forked context: traceId inherited, independent span tree
    withContext(Dispatchers.Default) {
        Operations.context.traceId  // available after every suspension point
        Operations.context.executionScope  // ASYNC
    }
}
```

### Context isolation per coroutine

Each child coroutine gets its own independent context. Parallel coroutines do not share or interfere with each other's span trees.

```kotlin
coroutineScope {
    async {
        // Forked context A — independent span tree rooted at async.execution
        checkInventory(request)
    }
    async {
        // Forked context B — independent span tree rooted at async.execution
        reservePayment(request)
    }
}
```

### Hook execution

The same configuration as `@Async` applies. Hooks fire automatically via `invokeOnCompletion` when the coroutine's `Job` completes — no DSL wrapper required.

```kotlin
// Enable hooks for coroutines launched in this request
Operations.context.enableAsyncHook()

launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    processEvent()  // hooks fire on coroutine completion
}
```

### Summary

| | Context access | Span tree | Hook execution |
|--|--|--|--|
| Main request thread | ✓ | ✓ Main tree | ✓ Always (servlet filter) |
| `@Async` thread | ✓ Forked (`executionScope=ASYNC`) | ✓ Independent | Optional — config or `enableAsyncHook()` |
| Coroutine child | ✓ Forked (`executionScope=ASYNC`) | ✓ Independent | Optional — config or `enableAsyncHook()` |
| Event handler (`@ManagedEventHandler`) | ✓ New (`executionScope=EVENT`) | ✓ Independent | ✓ Always |

---

## Event-Driven Context Propagation (`@ManagedEventHandler`)

Annotate a messaging handler method with `@ManagedEventHandler` to start an ENTRY-layer span and set `executionScope` to `EVENT`. No servlet filter is involved — the aspect manages the full context lifecycle.

### Automatic context extraction

OMK extracts trace metadata from method arguments using the following priority chain:

| Priority | Source | Condition |
|----------|--------|-----------|
| 1st | `@ManagedEvent*` field annotations | Parameter class has fields annotated with `@ManagedEventTraceId`, `@ManagedEventCausationId`, `@ManagedEventIssuer`, or `@ManagedEventType` |
| 2nd | Kafka `ConsumerRecord` headers | Argument is a `ConsumerRecord` — W3C `traceparent` parsed first (`00-{traceId}-{spanId}-{flags}`), then `X-Trace-Id` / `X-Causation-Id` |
| 3rd | Spring `Message<*>` headers | Argument implements `org.springframework.messaging.Message` — reads `MessageHeaders` |
| 4th | Duck typing | Reflection scans for fields or getters named `traceId`, `causationId`, `issuer`, `eventType` |
| 5th | `generate-when-missing` | If no context found: generate new IDs (default `true`) or inject empty strings (`false`) |

### Annotation-based extraction (highest priority)

Tag fields in your event or domain object with `@ManagedEvent*` annotations:

```kotlin
import io.github.hchanjune.omk.core.annotations.*

data class OrderCreatedEvent(
    @ManagedEventTraceId     val traceId: String,
    @ManagedEventCausationId val causationId: String,
    @ManagedEventIssuer      val issuer: String,
    @ManagedEventType        val eventType: String = "OrderCreated",
    val orderId: Long
)

@Component
class OrderEventHandler {

    @ManagedEventHandler
    @KafkaListener(topics = ["order.created"])
    fun handle(event: OrderCreatedEvent) {
        // Operations.context is available — executionScope = EVENT
        // traceId, causationId, issuer, eventType injected from annotated fields
    }
}
```

### Kafka `ConsumerRecord` (zero annotation needed)

If the handler receives a raw `ConsumerRecord`, OMK reads headers automatically — no annotation required.

```kotlin
@Component
class OrderEventHandler {

    @ManagedEventHandler
    @KafkaListener(topics = ["order.created"])
    fun handle(record: ConsumerRecord<String, String>) {
        // Reads W3C traceparent header; falls back to X-Trace-Id / X-Causation-Id
        // eventType is set to the topic name
    }
}
```

### Manual initialization (Outbox / Inbox pattern)

With the Transactional Outbox / Inbox pattern, events are stored in a database table before being processed. Trace metadata (`traceId`, `causationId`, etc.) lives in DB columns alongside the event payload — not in Kafka headers. The `@ManagedEventHandler` aspect cannot extract this metadata from the method argument, so you initialize the context manually.

#### How it works

When `Operations.initializeForEvent()` is called **before** the annotated handler method, the aspect detects an existing context (`contextOwner = false`) and skips auto-extraction. It still opens an ENTRY-layer span and records metrics — but **lifecycle ownership (hooks + clear) stays with the caller**.

This means the polling method is responsible for:
1. Calling `Operations.initializeForEvent()` with inbox metadata
2. Calling `handle()` **through the Spring proxy** (to allow the aspect to intercept — see note below)
3. Calling `Operations.complete()` after the handler returns
4. Firing `Operations.hook?.onSuccess()` / `Operations.hook?.onFailure()` explicitly
5. Calling `Operations.clear()` in `finally`

#### Complete `AbstractEventHandler` pattern

```kotlin
abstract class AbstractEventHandler<E : Event> : EventHandler<E>, ApplicationContextAware {

    private lateinit var applicationContext: ApplicationContext
    private lateinit var inboxStore: EventInboxStore
    private lateinit var eventSerializer: EventSerializer

    override fun setApplicationContext(ctx: ApplicationContext) {
        applicationContext = ctx
        inboxStore = ctx.getBean<EventInboxStore>()
        eventSerializer = ctx.getBean<EventSerializer>()
    }

    @Scheduled(fixedDelay = 500)
    open fun pollAndProcess() {
        inboxStore.findAllByEventTypeAndStatus(eventType, EventInboxStatus.RECEIVED, limit = 100)
            .filter { inboxStore.tryAcquire(it.eventId) }
            .forEach { processOne(it) }
    }

    protected fun processOne(record: EventInbox) {
        Operations.initializeForEvent(
            EventMetadata(
                traceId     = record.traceId,
                causationId = record.causationId,
                issuer      = record.issuer,
                eventType   = record.eventType
            )
        )
        val context = Operations.context
        try {
            val event = eventSerializer.deserialize(record.payload, eventClass.java)
            // Call through the Spring proxy so @ManagedEventHandler aspect fires
            applicationContext.getBean(this::class.java).handle(event)
            Operations.complete()
            Operations.hook?.onSuccess(context)
            inboxStore.markCompleted(record.eventId)
        } catch (e: Exception) {
            Operations.complete()
            Operations.hook?.onFailure(context, e)
            inboxStore.markFailed(record.eventId, e.message ?: "")
        } finally {
            Operations.clear()
        }
    }
}
```

#### Concrete handler

```kotlin
@Component
class OrderCreatedEventHandler : AbstractEventHandler<OrderCreatedEvent>() {

    override val eventType: String = "order.created"
    override val eventClass: KClass<OrderCreatedEvent> = OrderCreatedEvent::class

    @ManagedEventHandler
    override fun handle(event: OrderCreatedEvent) {
        // Operations.context is available here — executionScope = EVENT
        // traceId, causationId, issuer injected from the inbox record
    }
}
```

#### Why `applicationContext.getBean(this::class.java)`?

`processOne()` calls `handle()` internally. Spring AOP works through proxies — calling `this.handle()` bypasses the proxy and the `@ManagedEventHandler` aspect never fires. Fetching the bean from the application context returns the proxy, so the aspect intercepts the call correctly.

> `contextOwner = false` means the aspect manages only the span (open on entry, close on exit). It does **not** call `Operations.complete()`, fire hooks, or call `Operations.clear()`. The polling method owns those steps.

### Span tree

```
@ManagedEventHandler  ──  [ENT] root span  (executionScope = EVENT)
    └── @ManagedOperation  ──  [APP] child span
            └── @ManagedMetric      ──  [APP] child span
            └── @ManagedRepository  ──  [DB]  child span
```

---

## Thread Lifecycle and Context Isolation

Async boundaries produce **forked** contexts, not shared references.

**Main thread lifecycle (HTTP)**

```
request in → context created → business logic → hooks fired → clear() → response out
```

**Async/coroutine lifecycle**

```
Main thread:   [context A] ──── business logic ──── hooks ──── clear()
                      │ forkAsync()
Async thread:          └─── [context B: executionScope=ASYNC] ──── async work ──── (hooks if enabled) ──── clear()
```

**Event handler lifecycle**

```
message in → context created (executionScope=EVENT) → handler logic → hooks fired → clear()
```

The two async contexts share `traceId` and `causationId` for log correlation but have completely independent span trees, timing, and hook lifecycles. There is no concurrent mutation of shared state — each thread operates on its own context instance.

---

## Spring Security Integration

If Spring Security is on the classpath, the issuer is automatically resolved from the security context.

```
issuer = authentication.name  # authenticated user
issuer = "anonymous"          # unauthenticated
```

Spring Security is an **optional** dependency — the library works without it.

---

## Micrometer Integration

When `MeterRegistry` is available and `micrometer.enabled=true`, a Micrometer-backed `MetricsRecorder` bean is registered automatically.

When Micrometer is unavailable, a no-op `MetricsRecorder` is used as a fallback — the application starts normally.

To export metrics to Prometheus:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

Metrics will be exposed at `/actuator/prometheus`.

> Prometheus is **pull-based** — the library does not push metrics. Prometheus scrapes `/actuator/prometheus` on its own schedule. Ensure the endpoint is reachable from your Prometheus instance.

---

## Spring Logback Integration

OMK emits all operation results through two dedicated loggers:

| Logger name | Content |
|-------------|---------|
| `OperationManager.Pretty` | Human-readable box format (development) |
| `OperationManager.JSON` | Structured JSON (production / log aggregation) |

Because `OperationManager.JSON` produces a consistent JSON object per operation — containing `traceId`, `causationId`, `protocol`, `status`, `durationMs`, and more — **Loki + Grafana is the recommended production stack**. Loki can extract JSON fields as labels and stream filters, enabling dashboards and alerts built directly on operation metadata without a separate APM tool.

Route the two loggers independently in `logback-spring.xml`.

### Console only (development)

```xml
<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="OperationManager.JSON" level="OFF" additivity="false"/>
```

### Loki — Option A: Promtail (recommended)

Write the JSON logger to a dedicated file and let Promtail tail and forward it to Loki. Promtail buffers on disk and retries on Loki downtime — no log loss risk.

```xml
<!-- logback-spring.xml -->
<appender name="OMK_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/omk-operations.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/omk-operations.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder>
        <pattern>%message%n</pattern>
    </encoder>
</appender>

<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="OperationManager.JSON" level="INFO" additivity="false">
    <appender-ref ref="OMK_FILE"/>
</logger>
```

Point Promtail at `logs/omk-operations*.log` to complete the pipeline.

### Loki — Option B: `loki-logback-appender` (direct push)

The app pushes JSON logs directly to Loki over HTTP. No Promtail or sidecar required — simpler setup, but logs may be lost if Loki is temporarily unavailable.

```kotlin
// build.gradle.kts
implementation("com.github.loki4j:loki-logback-appender:1.5.x")
```

```xml
<!-- logback-spring.xml -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://loki:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <pattern>app=${APP_NAME},env=${ENV},level=%level</pattern>
        </label>
        <message>
            <pattern>%message</pattern>
        </message>
        <sortByTime>true</sortByTime>
    </format>
</appender>

<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="OperationManager.JSON" level="INFO" additivity="false">
    <appender-ref ref="LOKI"/>
</logger>
```

> `additivity="false"` is required on both loggers. Without it, log events bubble up to the root logger and appear in the console or file appender as well.

### Recommended setup per environment

| Environment | Pretty | JSON |
|-------------|--------|------|
| Local / dev | `CONSOLE` | `OFF` |
| Staging | `CONSOLE` | `LOKI` (either option) |
| Production | `OFF` | `LOKI` (Promtail recommended) |

---

## Extending the Library

You can replace any default provider by registering a custom bean.

| Interface | Purpose |
|-----------|---------|
| `TraceIdProvider` | Custom trace ID generation |
| `SpanIdProvider` | Custom span ID generation |
| `CausationIdProvider` | Custom causation ID generation |
| `IssuerProvider` | Custom issuer resolution |
| `TelemetryPropagationProvider` | Custom header propagation standard |
| `MetricsRecorder` | Custom metrics backend |
| `OperationHook` | Custom lifecycle callbacks |

---

## Notes & Limitations

- **Thread-local context**: `ManagedContext` is stored in `ThreadLocal`. `@Async` propagation is handled automatically via `ManagedContextTaskDecorator`. Kotlin coroutines require explicit propagation via `ManagedContextElement`. In fire-and-forget patterns (`launch`), hooks and span recording are outside the request lifecycle by design.
- **Spring AOP self-invocation**: AOP aspects do not intercept internal method calls within the same class.
- **Streaming responses**: The `traceparent` response header is set after request processing. For streaming or async responses, the header may not be delivered.
- **`Operations.context` scope**: Calling `Operations.context` outside a managed request scope (HTTP request, event handler, or manual `Operations.initializeForEvent()`) throws an `IllegalStateException` with a descriptive message.

---

## Roadmap

- [ ] Maven Central publishing
- [ ] WebFlux support
- [x] Async context propagation (`@Async` via `ManagedContextTaskDecorator`, coroutines via `ManagedContextElement`)
- [x] Span-level metric instrumentation (`@ManagedOperation`, `@ManagedMetric`, `@ManagedRepository` as DB spans)
- [x] Micrometer-backed `MetricsOperationHook` with full span tree recording
- [x] `@ManagedController` as ENTRY-layer root span; layer/timestamp/thread visible in span tree
- [x] Messaging context propagation (`@ManagedEventHandler` for Kafka, Spring Messaging, etc.; auto-extraction priority chain; manual `Operations.initializeForEvent()`)
- [ ] OpenTelemetry SDK integration
- [ ] Sampling support for high-throughput environments

---

## License

This project is open-source. Contributions are welcome.
