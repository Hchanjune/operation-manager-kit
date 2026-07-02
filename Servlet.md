# OMK — Spring WebMVC (Servlet Stack)

**[한국어](Servlet.ko.md) | English**

→ [Back to README](README.md)

---

## Table of Contents

- [Quick Start](#quick-start)
- [Annotations](#annotations)
- [Hook System](#hook-system)
- [Span Metrics](#span-metrics)
- [Configuration Properties](#configuration-properties)
- [`@Async` Context Propagation](#async-context-propagation)
- [Kotlin Coroutine Context Propagation](#kotlin-coroutine-context-propagation)
- [Event-Driven Context Propagation](#event-driven-context-propagation-managedeventhandler)
- [Spring Security Integration](#spring-security-integration)
- [Distributed Tracing](#distributed-tracing)
- [Observability Pipelines](#observability-pipelines)
- [Logback Integration](#logback-integration)
- [Extending the Library](#extending-the-library)
- [Notes & Limitations](#notes--limitations)

---

## Quick Start

### 1. Annotate your components

The recommended pattern is to place `@ManagedOperation` on the primary business logic handler in your service and return the result of `Operations { }` directly.

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

    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        val order = orderRepository.save(Order.from(request))
        OrderResponse.from(order)
    }

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

`Operations.context` is accessible globally from anywhere within the managed scope.

```kotlin
@Service
class AuditService {
    fun record() {
        val ctx = Operations.context
        println(ctx.traceId)
        println(ctx.issuer)
    }
}
```

Context state is cumulative and reflects the lifecycle at the point of access:

```kotlin
// At entry point       → traceId ✓  issuer ✓  service ✗  operation ✗
// In service           → traceId ✓  issuer ✓  service ✓  operation ✗
// In @ManagedOperation → all fields ✓
```

The `Operations { }` block also captures the result:

```kotlin
val result = Operations { // this: ManagedContext
    println(traceId)
    "OK"
}
println(result.context.traceId)
println(result.data) // "OK"
```

---

## Annotations

| Annotation             | Target | Purpose                                                                                            |
|------------------------|--------|----------------------------------------------------------------------------------------------------|
| `@ManagedController`   | Class  | Opens an ENTRY-layer root span per handler method; injects entrypoint into context                 |
| `@ManagedService`      | Class  | Injects service name into context                                                                  |
| `@ManagedRepository`   | Class  | Instruments all methods on a repository as DB-layer child spans                                    |
| `@ManagedOperation`    | Method | Injects `operation` and `useCase` into context; opens an APPLICATION-layer span                    |
| `@ManagedMetric`       | Method | Instruments any method as a named APPLICATION-layer child span                                     |
| `@ManagedEventHandler` | Method | Opens an ENTRY-layer span for messaging handlers; auto-extracts trace context from event arguments |

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

After `filterChain.doFilter()` returns without throwing, `ManagedContextPersistenceFilter` calls `context.injectStatusCode(response.status)`, which derives `context.outcome` (see [OperationOutcome](API.md#operationoutcome)) from the final HTTP status code:

- If `outcome == SERVER_ERROR` (5xx), `onFailure` is called — even when no exception was thrown (e.g. `response.sendError(500)` or `response.status = 500` set directly).
- For every other outcome, including `UNAUTHENTICATED` (401) and `FORBIDDEN` (403), `onSuccess` is still called — the request was handled and a response was returned as intended (e.g. Spring Security's `AuthenticationEntryPoint`).

`DefaultOperationLoggingHook` reads `context.outcome` inside `onSuccess` and logs non-`SUCCESS` outcomes at `logging.client-error-level` (default `WARN`) instead of `logging.success-level`.

### Exception Capture (`@ExceptionHandler` / `@ControllerAdvice`)

When a `@RestControllerAdvice` converts an exception into a normal response (the common pattern for domain/validation errors), that conversion happens **inside** `DispatcherServlet`, before control returns to `ManagedContextPersistenceFilter`. By the time the filter runs, the original exception object is gone — the filter only sees the final HTTP status code:

- For a `SERVER_ERROR` (5xx), the filter previously had to invent a placeholder `RuntimeException("HTTP 500")` for `onFailure`, since the real exception never reached it.
- For every other outcome, `onSuccess` was called with no exception information at all — a thrown `DomainValidationException` produced a log entry indistinguishable from a clean request.

`ExceptionCapturingResolver` is a `HandlerExceptionResolver` registered at `Ordered.HIGHEST_PRECEDENCE`. It runs before your `@ExceptionHandler` methods, records the exception onto `ManagedContext.capturedException`, then returns `null` so your actual exception handler still produces the response unchanged. The filter and `DefaultOperationLoggingHook` then read `context.capturedException` instead of fabricating one — `onFailure` gets the real exception for 5xx, and `onSuccess`'s log output now includes the real exception's type/message/stack trace for 4xx/401/403 too. `onSuccess` vs `onFailure` routing itself is unchanged (still status-code based, see above) — only which exception is visible changes.

This is enabled by default; disable with `operation-manager.webmvc.exception-capture.enabled: false`.

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
├─ HTTP_URI    : /orders
├─ HTTP_METHOD : POST
├─ Entry Point : OrderController
├─ Service     : OrderService
├─ Operation   : CreateOrder
├─ UseCase     : PlaceOrder
├─ Performance : 56Ms
├─ Spans      :
│    [ENT] 12:34:56.733  [nio-8080-exec-1]  OrderController.create   [56ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [nio-8080-exec-1]  CreateOrder      [48ms]   SUCCESS
│                   └─ [DB ] 12:34:56.751  [nio-8080-exec-1]  OrderRepository.save  [18ms]  SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

---

## Span Metrics

### Span Hierarchy

```
@ManagedController    ──  [ENT] root span
@ManagedEventHandler  ──  [ENT] root span  (executionScope = EVENT)
    └── @ManagedOperation  ──  [APP] child span
            └── @ManagedMetric      ──  [APP] child span
            └── @ManagedRepository  ──  [DB]  child span
```

Spans are flushed by `MetricsOperationHook` (Order 60) when the request completes, recorded to Micrometer as `omk.span.duration`.

---

## Configuration Properties

```yaml
operation-manager:
  webmvc:
    context-filter:
      enabled: true
      exclude-options: true   # if true, OPTIONS (CORS preflight) requests bypass context creation/logging entirely (default: true)

    context-aspect:
      enabled: true

    exception-capture:
      enabled: true       # registers ExceptionCapturingResolver so onFailure/logs see the real exception caught by @ExceptionHandler

    micrometer:
      enabled: true

    otel:
      enabled: true       # requires Tracer bean

    logging:
      enabled: true
      pretty: false
      json: true
      spans: false
      success-level: INFO
      failure-level: ERROR

    async-propagation:
      enabled: true
      hook-enabled: false

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        generate-when-missing: true
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## `@Async` Context Propagation

When `@EnableAsync` is active, `ManagedContextTaskDecorator` is automatically registered. When an `@Async` method is called, the context is **forked** — each async thread receives an independent copy.

| Field                              | Behavior       |
|------------------------------------|----------------|
| `traceId`, `causationId`, `issuer` | Inherited      |
| `executionScope`                   | Set to `ASYNC` |
| Span tree, timing, hook records    | Independent    |

### Java 21 Virtual Thread Support

```yaml
spring:
  threads:
    virtual:
      enabled: true
# No OMK-specific configuration needed
```

---

## Kotlin Coroutine Context Propagation

`ManagedContextElement` propagates the managed context across suspension points:

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    // Forked context: traceId inherited, independent span tree
    Operations.context.traceId  // available after every suspension point
}
```

---

## Event-Driven Context Propagation (`@ManagedEventHandler`)

### Automatic context extraction priority

| Priority | Source                                                                 |
|----------|------------------------------------------------------------------------|
| 1st      | `@ManagedEvent*` field annotations                                     |
| 2nd      | Kafka `ConsumerRecord` headers (W3C traceparent → X-Trace-Id fallback) |
| 3rd      | Spring `Message<*>` headers                                            |
| 4th      | Duck typing (reflection scan for `traceId`, `causationId`, etc.)       |
| 5th      | `generate-when-missing` (generate new IDs or inject empty strings)     |

```kotlin
@Component
class OrderEventHandler {

    @ManagedEventHandler
    @KafkaListener(topics = ["order.created"])
    fun handle(event: OrderCreatedEvent) {
        // Operations.context available — executionScope = EVENT
    }
}
```

---

## Spring Security Integration

If Spring Security is on the classpath, the issuer is automatically resolved:

```
issuer = authentication.name   # authenticated user
issuer = "anonymous"           # unauthenticated
```

Spring Security is an **optional** dependency.

---

## Distributed Tracing

W3C Trace Context is used by default:

```
traceparent: 00-{traceId 32hex}-{spanId 16hex}-01
```

Custom headers can be configured via `mode: CUSTOM`.

---

## Observability Pipelines

```
OMK span tree
    ├── MetricsOperationHook  →  Micrometer  →  Prometheus / Grafana Mimir
    └── OtelOperationHook     →  OpenTelemetry  →  Tempo / Jaeger / Zipkin
```

### Micrometer

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

### OpenTelemetry

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

```yaml
management:
  otlp:
    tracing:
      endpoint: http://tempo:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

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

- **Spring AOP self-invocation**: AOP aspects do not intercept internal method calls within the same class.
- **`Operations.context` scope**: Calling outside a managed scope throws `IllegalStateException`.
- **Streaming responses**: The `traceparent` response header may not be delivered for streaming or async responses.
- **Thread-local context**: `ManagedContext` is stored in `ThreadLocal`. Kotlin coroutines require explicit propagation via `ManagedContextElement`.

---