# OMK — Spring WebFlux (Reactive Stack)

**[한국어](Reactive.ko.md) | English**

→ [Back to README](README.md)

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Annotations](#annotations)
- [Hook System](#hook-system)
- [Span Metrics](#span-metrics)
- [Configuration Properties](#configuration-properties)
- [How Context Propagates](#how-context-propagates)
- [Event-Driven Context Propagation](#event-driven-context-propagation-managedeventhandler)
- [Spring Security Integration](#spring-security-integration)
- [Distributed Tracing](#distributed-tracing)
- [Observability Pipelines](#observability-pipelines)
- [Logback Integration](#logback-integration)
- [Extending the Library](#extending-the-library)
- [Notes & Limitations](#notes--limitations)

---

## Prerequisites

### Dependencies

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:core:x.x.x")
    implementation("com.github.Hchanjune.operation-manager-kit:reactive:x.x.x")

    implementation("org.aspectj:aspectjweaver")
    implementation("io.micrometer:micrometer-core")

    // kotlin-reflect version must match kotlin-stdlib
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

> **Important**: `kotlin-reflect` must be the same version as your `kotlin-stdlib`. A version mismatch (e.g., reflect 1.6 with stdlib 2.x) prevents Spring WebFlux from detecting `suspend fun` methods correctly, causing trace context to not propagate.

### Configuration

```yaml
operation-manager:
  reactive:
    context-aspect:
      enabled: true
    logging:
      pretty: true
      spans: true
    telemetry:
      propagation:
        mode: W3C_STANDARD
        generate-when-missing: true
```

---

## Quick Start

### 1. Annotate your components

`reactive` supports both `suspend fun` and `Mono`/`Flow` returning methods.

```kotlin
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.annotations.ManagedOperation

@RestController
@ManagedController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    // suspend fun — recommended for WebFlux + Coroutines
    @GetMapping("/{id}")
    suspend fun getById(@PathVariable id: String): OrderResponse =
        orderService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@RequestBody request: CreateOrderRequest): OrderResponse =
        orderService.create(request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(@PathVariable id: String) =
        orderService.delete(id)
}

@Service
@ManagedService
class OrderService(private val orderRepository: OrderRepository) {

    @ManagedOperation(operation = "FindOrder", useCase = "OrderManagement")
    suspend fun findById(id: String): OrderResponse {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        return OrderResponse.from(order)
    }

    @ManagedOperation(operation = "CreateOrder", useCase = "OrderManagement")
    suspend fun create(request: CreateOrderRequest): OrderResponse {
        val saved = orderRepository.save(Order.from(request))
        return OrderResponse.from(saved)
    }

    @ManagedOperation(operation = "DeleteOrder", useCase = "OrderManagement")
    suspend fun delete(id: String) {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        orderRepository.delete(order)
    }
}
```

### 2. Access context via ReactiveOperations

In a WebFlux + Coroutines application, use `ReactiveOperations` to access the managed context within a suspend function:

```kotlin
@Service
@ManagedService
class AuditService {

    @ManagedOperation(operation = "Audit", useCase = "Compliance")
    suspend fun record(): OperationResult<AuditResult> = ReactiveOperations {  // this: ManagedContext
        println(traceId)
        println(issuer)
        println(operation)
        AuditResult(traceId = traceId, issuer = issuer)
    }
}
```

The return type can be inferred — the explicit annotation is optional:

```kotlin
suspend fun record() = ReactiveOperations {
    AuditResult(traceId = traceId, issuer = issuer)
}

suspend fun record() = ReactiveOperations<AuditResult> {
    AuditResult(traceId = traceId, issuer = issuer)
}
```

`ReactiveOperations { }` reads the `ManagedContext` from the Reactor context propagated by the WebFilter — it is available anywhere in the coroutine call chain within a managed HTTP request.

Context state is cumulative:

```kotlin
// At controller entry → traceId ✓  issuer ✓  service ✗  operation ✗
// In service          → traceId ✓  issuer ✓  service ✓  operation ✗
// In @ManagedOperation → all fields ✓
```

---

## Annotations

| Annotation             | Target | Purpose                                                                                            |
|------------------------|--------|----------------------------------------------------------------------------------------------------|
| `@ManagedController`   | Class  | Opens an ENTRY-layer root span per handler method; injects entrypoint into context                 |
| `@ManagedService`      | Class  | Injects service name into context                                                                  |
| `@ManagedRepository`   | Class  | Instruments all methods on a repository as DB-layer child spans                                    |
| `@ManagedCacheRepository` | Class | Instruments all methods on a cache access class as CACHE-layer child spans (`[CAC]`)              |
| `@ManagedOperation`    | Method | Injects `operation` and `useCase` into context; opens an APPLICATION-layer span                    |
| `@ManagedMetric`       | Method | Instruments any method as a named APPLICATION-layer child span                                     |
| `@ManagedEventHandler` | Method | Opens an ENTRY-layer span for messaging handlers; auto-extracts trace context from event arguments |
| `@ManagedSchedule`     | Method | Opens an ENTRY-layer span for scheduler-triggered methods (e.g. `@Scheduled`); generates a fresh trace context |

> `@ManagedRepository` **currently** works on implementation classes only. Spring Data reactive repositories are interfaces (JDK proxies), so applying it directly to a `CoroutineCrudRepository` interface will not take effect — use `@ManagedMetric` on the calling service methods instead. Interface support is on the [roadmap](README.md#roadmap).

---

## Hook System

### Hook Ordering

```
Order < 50  →  Pre-logging hooks      (context enrichment, preprocessing)
Order 50    →  DefaultOperationLoggingHook
Order 60    →  MetricsOperationHook
Order > 60  →  Custom post-logging hooks
```

### Outcome Classification (onSuccess vs onFailure)

In the `beforeCommit` hook, `ManagedContextWebFilter` calls `context.injectStatusCode(statusCode)` with the final HTTP response status code, which derives `context.outcome` (see [OperationOutcome](API.md#operationoutcome)):

- If `outcome == SERVER_ERROR` (5xx), `onFailure` is called.
- For every other outcome, including `UNAUTHENTICATED` (401) and `FORBIDDEN` (403), `onSuccess` is called — the request was handled and a response was returned as intended (e.g. Spring Security's `AuthenticationEntryPoint`).

`DefaultOperationLoggingHook` reads `context.outcome` inside `onSuccess` and logs non-`SUCCESS` outcomes at `logging.client-error-level` (default `WARN`) instead of `logging.success-level`.

### Exception Capture (`@ExceptionHandler` / `@ControllerAdvice`)

When a `@RestControllerAdvice` recovers from an exception via `onErrorResume` inside the same reactive chain, the original exception object never reaches `beforeCommit` — by the time it runs, only the final response status code is observable. There is no `HandlerExceptionResolver`-equivalent SPI in WebFlux to intercept this transparently, so the fix reuses `ManagedControllerAspect` instead: it already wraps every `@ManagedController` method in a `doOnError`/`catch` to mark the ENTRY span as failed, and `doOnError` fires on the real exception *before* any downstream `onErrorResume` recovers from it (standard Reactor semantics — peek operators upstream of a recovery still observe the error). The aspect now also calls `ctx.recordException(e)` at that exact point, storing it on `ManagedContext.capturedException` — the same field the WebMVC fix uses.

`ManagedContextWebFilter` and `DefaultOperationLoggingHook` read `context.capturedException` instead of fabricating a placeholder: `onFailure` gets the real exception for 5xx, and `onSuccess`'s log output now includes the real exception's type/message/stack trace for 4xx/401/403 too. `onSuccess` vs `onFailure` routing itself is unchanged (still status-code based, see above).

This only covers exceptions that propagate out of a `@ManagedController`-annotated method — the same precondition the rest of this library's controller-level instrumentation already requires.

### Custom Hook Example

```kotlin
@Component
@Order(30)
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

| Property        | Default | Description                                |
|-----------------|---------|--------------------------------------------|
| `pretty`        | `false` | Human-readable formatted output            |
| `json`          | `true`  | JSON format (recommended for production)   |
| `spans`         | `false` | Append span tree to pretty output          |
| `response`      | `true`  | Include the operation block's return value |
| `ip`            | `false` | Include the client IP (opt-in — IP is treated as PII in many jurisdictions) |
| `success-level` | `INFO`  | Log level for successful operations        |
| `failure-level` | `ERROR` | Log level for failed operations            |
| `client-error-level` | `WARN` | Log level for `onSuccess` calls whose `outcome` is not `SUCCESS` (e.g. `UNAUTHENTICATED`, `FORBIDDEN`, `CLIENT_ERROR`) |

**Pretty output (with `spans: true`):**
```
┌───────────────────────────────────────────────────────────────────────────────────
│ ✅ Success
├─ Status      : SUCCESS
├─ TraceId     : 4bf92f3577b34da6a3ce929d0e0e4736
├─ CausationId : 00f067aa0ba902b7
├─ Issuer      : john.doe
├─ Protocol    : HTTP
├─ HTTP_URI    : /api/orders
├─ HTTP_METHOD : POST
├─ Entry Point : OrderController
├─ Service     : OrderService
├─ Operation   : CreateOrder
├─ UseCase     : OrderManagement
├─ Performance : 42Ms
├─ Spans      :
│    [ENT] 12:34:56.733  [reactor-http-nio-2]  OrderController.create   [42ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [reactor-http-nio-2]  CreateOrder      [35ms]   SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

> Spans typically show the same thread (`reactor-http-nio-*`) because WebFlux uses a non-blocking event loop. This is expected and correct — thread switching only occurs when explicitly using `Dispatchers.IO` for blocking operations.

---

## Span Metrics

### Span Hierarchy

```
@ManagedController    ──  [ENT] root span
@ManagedEventHandler  ──  [ENT] root span  (executionScope = EVENT)
@ManagedSchedule      ──  [ENT] root span  (executionScope = SCHEDULED)
    └── @ManagedOperation  ──  [APP] child span
            └── @ManagedMetric           ──  [APP] child span
            └── @ManagedRepository       ──  [DB]  child span
            └── @ManagedCacheRepository  ──  [CAC] child span
```

Spans are flushed by `MetricsOperationHook` (Order 60) when the request completes, recorded to Micrometer as `omk.span.duration`.

---

## Configuration Properties

```yaml
operation-manager:
  reactive:
    context-filter:
      enabled: true       # Enable/disable the WebFilter (default: true)
      exclude-options: true   # if true, OPTIONS (CORS preflight) requests bypass context creation/logging entirely (default: true)

    context-aspect:
      enabled: true       # Enable/disable AOP aspects (default: true)

    micrometer:
      enabled: true       # Enable/disable Micrometer metrics (default: true)

    logging:
      enabled: true
      pretty: false       # Human-readable format
      json: true          # JSON format (recommended for production)
      spans: false        # Append span tree to pretty output (default: false)
      success-level: INFO
      failure-level: ERROR

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        generate-when-missing: true
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## How Context Propagates

The `ManagedContextWebFilter` creates a `ManagedContext` and stores it in the Reactor context via `.contextWrite(...)` before the request reaches the controller. All suspend functions in the call chain inherit this Reactor context automatically.

```
Request → ManagedContextWebFilter (contextWrite)
              ↓
          Coroutines Utils (mono { ... })
              ↓
          Controller (suspend fun) → AOP aspect reads context from ReactorContext
              ↓
          Service (suspend fun)   → AOP aspect reads context from ReactorContext
              ↓
          Response → beforeCommit → hook.onSuccess/onFailure
```

The AOP aspects read the `ManagedContext` from the continuation's `ReactorContext` — no ThreadLocal is involved. This is why context propagates correctly across suspension points and thread switches in WebFlux.

---

## Event-Driven Context Propagation (`@ManagedEventHandler`)

`@ManagedEventHandler` supports both `Mono`-returning and `suspend fun` handlers.

### Automatic context extraction priority

| Priority | Source                                                                 |
|----------|------------------------------------------------------------------------|
| 1st      | `@ManagedEvent*` field annotations                                     |
| 2nd      | Kafka `ConsumerRecord` headers (W3C traceparent → X-Trace-Id fallback) |
| 3rd      | Spring `Message<*>` headers                                            |
| 4th      | Duck typing (reflection scan for `traceId`, `causationId`, etc.)       |
| 5th      | `generate-when-missing`                                                |

```kotlin
@Component
class OrderEventHandler {

    @ManagedEventHandler
    suspend fun handle(event: OrderCreatedEvent) {
        // ReactiveOperations { } available here — executionScope = EVENT
    }
}
```

---

## Spring Security Integration

If Spring Security (`spring-boot-starter-security` for WebFlux) is on the classpath, the issuer is automatically resolved from `ReactiveSecurityContextHolder`:

```
issuer = authentication.name   # authenticated user
issuer = "anonymous"           # unauthenticated
```

Without Spring Security, the issuer defaults to `"anonymous"`.

---

## Distributed Tracing

W3C Trace Context is used by default:

```
traceparent: 00-{traceId 32hex}-{spanId 16hex}-01
```

Custom headers:

```yaml
operation-manager:
  reactive:
    telemetry:
      propagation:
        mode: CUSTOM
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## Observability Pipelines

```
OMK span tree
    ├── MetricsOperationHook       →  Micrometer  →  Prometheus / Grafana Mimir
    └── OtelSpanBridge (live)      →  OpenTelemetry  →  Tempo / Jaeger / Zipkin
```

### Micrometer

```kotlin
implementation("io.micrometer:micrometer-core")
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

### OpenTelemetry

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

When a `Tracer` bean exists, OMK spans are **live OTel spans**: each `push` starts a real
span at that moment and the OTel-generated ids are adopted as the OMK ids, so the
`spanId`/`traceId` in OMK logs are exactly what you search for in Tempo/Jaeger. Incoming
`traceparent` headers (W3C mode) continue the upstream trace. Spans may start and end on
different event-loop threads — no thread-bound scopes are used; the span's OTel context
travels through the Reactor context instead.

To have OTel auto-instrumented reactive clients (`WebClient`, R2DBC, Lettuce, ...) nest
under the enclosing OMK span, add the Reactor instrumentation library — OMK then publishes
each span into the Reactor context under the key those instrumentations read:

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1:2.16.0-alpha")
```

Without it (or without a `Tracer` bean) everything else still works; the nesting is the only
part that needs the extra library. Disable explicitly with
`operation-manager.reactive.otel.enabled: false`.

---

## Logback Integration

| Logger                    | Content                      |
|---------------------------|------------------------------|
| `OperationManager.Pretty` | Human-readable box format    |
| `OperationManager.JSON`   | Structured JSON (production) |

```xml
<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>
<logger name="OperationManager.JSON" level="OFF" additivity="false"/>
```

---

## Extending the Library

| Interface                      | Purpose                        |
|--------------------------------|--------------------------------|
| `TraceIdProvider`              | Custom trace ID generation     |
| `SpanIdProvider`               | Custom span ID generation      |
| `CausationIdProvider`          | Custom causation ID generation |
| `IssuerProvider`               | Custom issuer resolution       |
| `TelemetryPropagationProvider` | Custom header propagation      |
| `MetricsRecorder`              | Custom metrics backend         |
| `OperationHook`                | Custom lifecycle callbacks     |

---

## Notes & Limitations

- **`kotlin-reflect` version**: Must match `kotlin-stdlib`. A mismatch causes Spring WebFlux to fall back to a non-Kotlin invocation path, breaking suspend fun detection and context propagation.
- **Spring AOP self-invocation**: AOP aspects do not intercept internal method calls within the same class.
- **`@ManagedRepository` on interfaces**: currently implementation classes only — Spring Data reactive repositories are interfaces, so the annotation has no effect on them directly. Use `@ManagedMetric` on service methods instead (interface support is on the [roadmap](README.md#roadmap)).
- **Event loop thread**: In a properly non-blocking application, spans will show the same `reactor-http-nio-*` thread. This is expected behavior — switching to `Dispatchers.IO` is only necessary for blocking operations.
- **`ReactiveOperations` scope**: Calling `ReactiveOperations { }` outside a managed scope logs a WARN and proceeds with a detached (unmanaged) context — the business logic runs, but spans and hooks are not recorded. Annotate the entry point (`@ManagedSchedule`, `@ManagedEventHandler`) to get a real managed scope.

---