# OMK — API Reference

**[한국어](API.ko.md) | English**

→ [Back to README](README.md)

---

## Annotations

### `@ManagedController`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedController
```

Applied to a `@RestController` or `@Controller` class. The AOP aspect intercepts every handler method and opens an **ENTRY-layer** root span for each invocation.

---

### `@ManagedService`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedService
```

Applied to a `@Service` class. Injects the class name into `ManagedContext.service`.

---

### `@ManagedRepository`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedRepository
```

Applied to a `@Repository` class. Instruments every method as a **DB-layer** child span.

> Not applicable to Spring Data repository interfaces (`JpaRepository`, `CoroutineCrudRepository`, etc.) — use `@ManagedMetric` on the service method instead.

---

### `@ManagedOperation`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedOperation(
    val operation: String = "",
    val useCase: String = ""
)
```

Applied to a service method. Injects `operation` and `useCase` into context and opens an **APPLICATION-layer** span.

| Parameter   | Type     | Default | Description                           |
|-------------|----------|---------|---------------------------------------|
| `operation` | `String` | `""`    | Operation name (e.g. `"CreateOrder"`) |
| `useCase`   | `String` | `""`    | Use case name (e.g. `"PlaceOrder"`)   |

---

### `@ManagedMetric`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedMetric(
    val name: String = ""
)
```

Applied to any method. Opens an **APPLICATION-layer** child span with the given name. Defaults to `ClassName.methodName` if `name` is blank.

| Parameter | Type     | Default | Description                                     |
|-----------|----------|---------|-------------------------------------------------|
| `name`    | `String` | `""`    | Span name. Falls back to `ClassName.methodName` |

---

### `@ManagedEventHandler`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedEventHandler
```

Applied to a messaging handler method (Kafka, Spring Messaging, etc.). Opens an **ENTRY-layer** span and sets `executionScope` to `EVENT`. Auto-extracts trace metadata from method arguments using the extraction priority chain.

---

### Event field annotations

Applied to fields in event/domain objects for automatic trace context extraction.

| Annotation                 | Field type | Extracted to                 |
|----------------------------|------------|------------------------------|
| `@ManagedEventTraceId`     | `String`   | `ManagedContext.traceId`     |
| `@ManagedEventCausationId` | `String`   | `ManagedContext.causationId` |
| `@ManagedEventIssuer`      | `String`   | `ManagedContext.issuer`      |
| `@ManagedEventType`        | `String`   | `ManagedContext.type`        |

```kotlin
data class OrderCreatedEvent(
    @ManagedEventTraceId     val traceId: String,
    @ManagedEventCausationId val causationId: String,
    @ManagedEventIssuer      val issuer: String,
    @ManagedEventType        val eventType: String,
    val orderId: Long
)
```

---

## ManagedContext

The central object carrying all metadata for a single operation lifecycle.

```kotlin
class ManagedContext
```

### Read-only fields

| Field            | Type                  | Description                                              |
|------------------|-----------------------|----------------------------------------------------------|
| `traceId`        | `String`              | Distributed trace ID (W3C or custom)                     |
| `causationId`    | `String`              | Parent span / causation ID                               |
| `issuer`         | `String`              | Authenticated user name, or `"anonymous"`                |
| `protocol`       | `ManagedProtocolType` | `HTTP`, `MESSAGING`, `RPC`, `DB`, etc.                   |
| `type`           | `String`              | Operation type: `"API"`, `"EVENT"`, `"BATCH"`, etc.      |
| `uri`            | `String`              | HTTP request URI                                         |
| `method`         | `String`              | HTTP method (`"GET"`, `"POST"`, etc.)                    |
| `entrypoint`     | `String`              | Controller class name                                    |
| `service`        | `String`              | Service class name                                       |
| `operation`      | `String`              | `@ManagedOperation.operation` value                      |
| `useCase`        | `String`              | `@ManagedOperation.useCase` value                        |
| `response`       | `String`              | `toString()` of the `Operations { }` return value        |
| `timestamp`      | `Instant`             | Context creation time (UTC)                              |
| `durationMs`     | `Long`                | Total execution time in milliseconds                     |
| `executionScope` | `ExecutionScope`      | `PRIMARY`, `ASYNC`, or `EVENT`                           |
| `rootSpan`       | `MetricSpan?`         | Root of the span tree; `null` until first span is pushed |
| `hookRecords`    | `List<HookRecord>`    | Execution results of each hook                           |

### Mutable fields

| Field                | Type      | Description                                                                      |
|----------------------|-----------|----------------------------------------------------------------------------------|
| `message`            | `String`  | Free-form label; set in a hook before `DefaultOperationLoggingHook` (Order < 50) |
| `isAsyncHookEnabled` | `Boolean` | Whether hooks fire for async children of this context                            |

### Methods

| Method               | Description                                               |
|----------------------|-----------------------------------------------------------|
| `enableAsyncHook()`  | Enables hook execution for `@Async` / coroutine children  |
| `disableAsyncHook()` | Disables hook execution for `@Async` / coroutine children |

### HookRecord

```kotlin
data class HookRecord(
    val hookName: String,
    val success: Boolean,
    val error: Throwable? = null
)
```

---

## Operations (WebMVC)

Global singleton. Thread-local context accessor for the Servlet stack.

```kotlin
object Operations
```

### Properties

| Property     | Type             | Description                                                                       |
|--------------|------------------|-----------------------------------------------------------------------------------|
| `context`    | `ManagedContext` | Current context. Throws `IllegalStateException` if called outside a managed scope |
| `hasContext` | `Boolean`        | `true` if a context exists on the current thread                                  |
| `hook`       | `OperationHook?` | Composite hook instance                                                           |

### Operators

```kotlin
operator fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T>
```

Executes `block` within the current context, captures the return value as `OperationResult.data`, and injects `response` into the context. Receiver `this` is the `ManagedContext`.

```kotlin
val result = Operations {
    // this: ManagedContext
    val order = repo.save(Order.from(request))
    OrderResponse.from(order)           // captured as result.data
}
result.data    // OrderResponse
result.context // ManagedContext
```

### Static methods

| Method                                        | Description                                                       |
|-----------------------------------------------|-------------------------------------------------------------------|
| `initializeForEvent(metadata: EventMetadata)` | Manually initializes context for messaging (Outbox/Inbox pattern) |
| `complete()`                                  | Stops the context timer                                           |
| `clear()`                                     | Removes context from `ThreadLocal`                                |

---

## ReactiveOperations (WebFlux)

Reactive equivalent of `Operations`. Reads context from the Reactor context.

```kotlin
object ReactiveOperations
```

### Suspend operator

```kotlin
suspend operator fun <T> invoke(block: suspend ManagedContext.() -> T): OperationResult<T>
```

```kotlin
val result = ReactiveOperations {
    // this: ManagedContext
    AuditResult(traceId = traceId, issuer = issuer)
}
result.data    // AuditResult
result.context // ManagedContext
```

### Mono helper

```kotlin
fun <T : Any> mono(block: ManagedContext.() -> Mono<T>): Mono<OperationResult<T>>
```

```kotlin
val result: Mono<OperationResult<MyData>> = ReactiveOperations.mono {
    Mono.just(MyData())
}
```

### Properties

| Property | Type             | Description             |
|----------|------------------|-------------------------|
| `hook`   | `OperationHook?` | Composite hook instance |

---

## OperationHook

Lifecycle callback interface.

```kotlin
interface OperationHook {
    fun onSuccess(context: ManagedContext) {}
    fun onFailure(context: ManagedContext, exception: Throwable) {}
}
```

Register as a Spring `@Component`. Ordering is controlled via `@Order`.

```kotlin
@Component
@Order(30)
class MyHook : OperationHook {
    override fun onSuccess(context: ManagedContext) { ... }
    override fun onFailure(context: ManagedContext, exception: Throwable) { ... }
}
```

Built-in hooks:

| Hook                          | Order | Description                                                    |
|-------------------------------|-------|----------------------------------------------------------------|
| `DefaultOperationLoggingHook` | 50    | Logs operation result (pretty / JSON)                          |
| `MetricsOperationHook`        | 60    | Records span tree to Micrometer                                |
| `OtelOperationHook`           | 70    | Exports spans to OpenTelemetry (when `Tracer` bean is present) |

---

## OperationResult\<T\>

```kotlin
data class OperationResult<T>(
    val context: ManagedContext,
    val data: T
)
```

Returned by `Operations { }` and `ReactiveOperations { }`.

---

## MetricSpan

A single timed unit of work in the span tree.

```kotlin
class MetricSpan
```

| Field              | Type               | Description                                              |
|--------------------|--------------------|----------------------------------------------------------|
| `traceId`          | `String`           | Trace ID inherited from context                          |
| `spanId`           | `String`           | Unique span ID                                           |
| `name`             | `MetricName`       | Span name                                                |
| `threadName`       | `String`           | Thread that started this span                            |
| `startTime`        | `Long?`            | Start time (epoch millis)                                |
| `durationMs`       | `Long?`            | Duration in milliseconds; `null` until `end()` is called |
| `outcome`          | `MetricOutcome?`   | Result; `null` until ended                               |
| `descriptor.layer` | `MetricLayer`      | `ENTRY`, `APPLICATION`, or `DB`                          |
| `children`         | `List<MetricSpan>` | Child spans                                              |
| `parent`           | `MetricSpan?`      | Parent span                                              |

---

## MetricOutcome

```kotlin
data class MetricOutcome(
    val status: MetricStatus,
    val errorType: String?,
    val errorMessage: String?
)
```

### MetricStatus

| Value             | Condition                                           |
|-------------------|-----------------------------------------------------|
| `SUCCESS`         | Normal completion                                   |
| `FAILURE_CLIENT`  | `IllegalArgumentException`, `IllegalStateException` |
| `FAILURE_SERVER`  | Other exceptions                                    |
| `CANCELLED`       | `CancellationException`                             |
| `PARTIAL_SUCCESS` | —                                                   |
| `UNKNOWN`         | —                                                   |

---

## EventMetadata

```kotlin
data class EventMetadata(
    val traceId: String? = null,
    val causationId: String? = null,
    val issuer: String? = null,
    val eventType: String? = null
)
```

Used with `Operations.initializeForEvent()` and `ReactiveOperations.initializeForEvent()` for manual event context initialization (Outbox/Inbox pattern).

---

## ExecutionScope

```kotlin
enum class ExecutionScope {
    PRIMARY,  // Main HTTP / event loop thread
    ASYNC,    // @Async thread or forked coroutine
    EVENT     // @ManagedEventHandler
}
```

---

## ManagedProtocolType

```kotlin
enum class ManagedProtocolType {
    HTTP, RPC, MESSAGING, FAAS, DB, UNSUPPORTED
}
```

---

## Provider Interfaces

Replace default implementations by registering a `@Bean` of the matching interface.

### `TraceIdProvider`

```kotlin
interface TraceIdProvider {
    fun provideTraceId(): String
}
```

### `CausationIdProvider`

```kotlin
interface CausationIdProvider {
    fun provideCausationId(): String
}
```

### `SpanIdProvider`

```kotlin
interface SpanIdProvider {
    fun provideSpanId(): String
}
```

### `IssuerProvider`

```kotlin
fun interface IssuerProvider {
    fun currentIssuer(): String
}
```

Default behavior:
- **WebMVC**: reads from `SecurityContextHolder` (Spring Security) or `"anonymous"` fallback
- **WebFlux**: reads from `ReactiveSecurityContextHolder` (Spring Security) or `"anonymous"` fallback

### `TelemetryPropagationProvider`

```kotlin
interface TelemetryPropagationProvider {
    fun extractTraceId(headerReader: (String) -> String?): String?
    fun extractParentId(headerReader: (String) -> String?): String?
    fun inject(traceId: String, spanId: String, headerWriter: (String, String) -> Unit)
}
```

### `ManagedContextProvider`

```kotlin
interface ManagedContextProvider {
    fun provide(): ManagedContext
}
```

### `MetricsRecorder`

```kotlin
interface MetricsRecorder {
    fun record(span: MetricSpan, context: ManagedContext)
}
```

Default implementation uses Micrometer. Falls back to no-op when `MeterRegistry` is unavailable.

---

## Configuration Properties

### WebMVC — `operation-manager.webmvc`

| Property                                            | Type              | Default          | Description                                 |
|-----------------------------------------------------|-------------------|------------------|---------------------------------------------|
| `context-filter.enabled`                            | `Boolean`         | `true`           | Enable servlet filter                       |
| `context-aspect.enabled`                            | `Boolean`         | `true`           | Enable AOP aspects                          |
| `micrometer.enabled`                                | `Boolean`         | `true`           | Enable Micrometer recording                 |
| `otel.enabled`                                      | `Boolean`         | `true`           | Enable OTel export (requires `Tracer` bean) |
| `logging.enabled`                                   | `Boolean`         | `true`           | Enable default logging hook                 |
| `logging.pretty`                                    | `Boolean`         | `false`          | Pretty-print format                         |
| `logging.json`                                      | `Boolean`         | `true`           | JSON format                                 |
| `logging.spans`                                     | `Boolean`         | `false`          | Include span tree in pretty output          |
| `logging.response`                                  | `Boolean`         | `true`           | Include operation return value in log       |
| `logging.success-level`                             | `LogLevel`        | `INFO`           | Log level for successful operations         |
| `logging.failure-level`                             | `LogLevel`        | `ERROR`          | Log level for failed operations             |
| `async-propagation.enabled`                         | `Boolean`         | `true`           | Enable `@Async` context propagation         |
| `async-propagation.hook-enabled`                    | `Boolean`         | `false`          | Run hooks on async task completion          |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` or `CUSTOM`                  |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | Generate IDs when not in request headers    |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | Custom trace ID header name                 |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | Custom causation ID header name             |

### WebFlux — `operation-manager.webflux`

| Property                                            | Type              | Default          | Description                              |
|-----------------------------------------------------|-------------------|------------------|------------------------------------------|
| `context-filter.enabled`                            | `Boolean`         | `true`           | Enable WebFilter                         |
| `context-aspect.enabled`                            | `Boolean`         | `true`           | Enable AOP aspects                       |
| `micrometer.enabled`                                | `Boolean`         | `true`           | Enable Micrometer recording              |
| `logging.enabled`                                   | `Boolean`         | `true`           | Enable default logging hook              |
| `logging.pretty`                                    | `Boolean`         | `false`          | Pretty-print format                      |
| `logging.json`                                      | `Boolean`         | `true`           | JSON format                              |
| `logging.spans`                                     | `Boolean`         | `false`          | Include span tree in pretty output       |
| `logging.response`                                  | `Boolean`         | `true`           | Include operation return value in log    |
| `logging.success-level`                             | `LogLevel`        | `INFO`           | Log level for successful operations      |
| `logging.failure-level`                             | `LogLevel`        | `ERROR`          | Log level for failed operations          |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` or `CUSTOM`               |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | Generate IDs when not in request headers |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | Custom trace ID header name              |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | Custom causation ID header name          |

---