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

Mark controllers, services, and repositories with the provided annotations.

```kotlin
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.annotations.ManagedRepository
import io.github.hchanjune.omk.core.annotations.ManagedOperation

@ManagedController
@RestController
class OrderController(private val orderService: OrderService) {

    @PostMapping("/orders")
    fun create(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        return ResponseEntity.ok(orderService.create(request))
    }
}

@ManagedService
@Service
class OrderService(private val orderRepository: OrderRepository) {

    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest): OrderResponse { ... }
}

@ManagedRepository
@Repository
class OrderRepository {
    fun save(order: Order): Order { ... }
}
```

### 2. Access context via Operations

Access the current request context anywhere within the managed scope.

```kotlin
import io.github.hchanjune.omk.webmvc.Operations

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
| `@ManagedController` | Class | Injects entrypoint (controller class name) into context |
| `@ManagedService` | Class | Injects service name into context |
| `@ManagedRepository` | Class | Marks repository for future metric instrumentation |
| `@ManagedOperation` | Method | Injects `operation` and `useCase` into context |

> All annotations inject low-cardinality class-level identifiers only — method names are intentionally excluded to prevent metric cardinality explosion.

---

## Hook System

Hooks allow you to react to operation lifecycle events (success or failure).

### Hook Ordering

Hooks are ordered using Spring's `@Order` annotation. The built-in `DefaultOperationLoggingHook` is registered at **Order 50**, which serves as the reference point.

```
Order < 50  →  Pre-logging hooks   (context enrichment, preprocessing)
Order 50    →  DefaultOperationLoggingHook  (logs the fully enriched context)
Order > 50  →  Post-logging hooks  (metrics recording, notifications, etc.)
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

**Pretty output:**
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
├─ Performance : 42Ms
├─ Hooks       : MyEnrichmentHook=OK
└───────────────────────────────────────────────────────────────────────────────────
```

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
      success-level: INFO
      failure-level: ERROR

    async-propagation:
      enabled: true       # Enable/disable @Async context propagation (default: true)

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## `@Async` Context Propagation

When `@EnableAsync` is active, `ManagedContextTaskDecorator` is automatically registered and applied to the default `ThreadPoolTaskExecutor`. The managed context (trace ID, issuer, operation metadata) is propagated to async threads without any additional configuration.

```kotlin
@Service
class NotificationService {

    @Async
    fun sendEmail(to: String) {
        val traceId = Operations.context.traceId  // inherited from the calling thread
        log.info("[{}] Sending email to {}", traceId, to)
    }
}
```

**What is propagated:** `traceId`, `issuer`, `entrypoint`, `service`, `operation`, `useCase`, and all other context fields.

**What is not propagated:** Hook execution and logging lifecycle. Hooks run on the main request thread when the HTTP request completes. Async tasks that outlive the request are outside the hook lifecycle by design — `@Async` is a side-effect mechanism, not a continuation of the main request flow.

To disable:

```yaml
operation-manager:
  webmvc:
    async-propagation:
      enabled: false
```

> If you have a custom `AsyncConfigurer` or a custom `ThreadPoolTaskExecutor`, inject `ManagedContextTaskDecorator` and apply it manually.

---

## Kotlin Coroutine Context Propagation

When `kotlinx-coroutines-core` is on the classpath, `ManagedContextElement` can be used to propagate the managed context across coroutine suspension points and thread switches.

Unlike `TaskDecorator` which only captures context at task submission, `ManagedContextElement` hooks into the coroutine dispatcher — restoring the `ThreadLocal` context on every thread the coroutine runs on, including after every suspension point.

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    withContext(Dispatchers.Default) {
        Operations.context.traceId  // available after every suspension point
    }
}
```

### Behavior by usage pattern

**Structured concurrency — context and spans propagate**

When child coroutines are awaited before the request completes, their work is visible when hooks run.

```kotlin
@ManagedService
@Service
class OrderService {

    @ManagedOperation(operation = "PlaceOrder")
    suspend fun place(request: OrderRequest) = coroutineScope {
        val inventory = async { checkInventory(request) }
        val payment   = async { reservePayment(request) }
        awaitAll(inventory, payment)
        // request completes here → hooks fire → spans are visible
    }
}
```

**Fire-and-forget (`launch`) — same limitation as `@Async`**

If the coroutine outlives the request, hooks have already fired by the time it completes. Context fields (traceId, issuer) are still accessible for direct logging, but hook-based logging and span recording are outside the request lifecycle.

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    sendNotification()  // may run after response is already sent
}
```

### Summary

| Pattern | `Operations.context` access | Span propagation | Hook execution |
|---------|----------------------------|------------------|----------------|
| Structured (`async`/`coroutineScope`) | ✓ | ✓ | ✓ |
| Fire-and-forget (`launch`) | ✓ | ✗ | ✗ |

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
- **`Operations.context` scope**: Calling `Operations.context` outside a managed request scope throws an `IllegalStateException` with a descriptive message.

---

## Roadmap

- [ ] Maven Central publishing
- [ ] WebFlux support
- [x] Async context propagation (`@Async` via `ManagedContextTaskDecorator`, coroutines via `ManagedContextElement`)
- [ ] OpenTelemetry SDK integration
- [ ] `@ManagedRepository` metric instrumentation
- [ ] Sampling support for high-throughput environments

---

## License

This project is open-source. Contributions are welcome.
