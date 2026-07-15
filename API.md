# OMK — API Reference

**[한국어](API.ko.md) | English**

→ [Back to README](README.md)

---

## Table of Contents

- [Annotations](#annotations)
  - [`@ManagedController`](#managedcontroller)
  - [`@ManagedService`](#managedservice)
  - [`@ManagedRepository`](#managedrepository)
  - [`@ManagedCacheRepository`](#managedcacherepository)
  - [`@ManagedOperation`](#managedoperation)
  - [`@ManagedMetric`](#managedmetric)
  - [`@ManagedEventHandler`](#managedeventhandler)
  - [`@ManagedSchedule`](#managedschedule)
  - [Event field annotations](#event-field-annotations)
- [ManagedContext](#managedcontext)
- [Operations (WebMVC)](#operations-webmvc)
- [ReactiveOperations (WebFlux)](#reactiveoperations-webflux)
- [OperationHook](#operationhook)
- [OperationResult\<T\>](#operationresultt)
- [MetricSpan](#metricspan)
- [MetricOutcome](#metricoutcome)
- [EventMetadata](#eventmetadata)
- [ExecutionScope](#executionscope)
- [ManagedProtocolType](#managedprotocoltype)
- [OperationOutcome](#operationoutcome)
- [Provider Interfaces](#provider-interfaces)
- [Configuration Properties](#configuration-properties)

---

## Annotations

> All class-level annotations (`@ManagedController`, `@ManagedService`, `@ManagedRepository`, `@ManagedCacheRepository`) are `@Inherited` — annotating an abstract base class also covers methods declared in its subclasses. Method-level annotations follow standard Java semantics: they apply to the declaring method (including when inherited un-overridden), but an override must re-declare the annotation.

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

### `@ManagedCacheRepository`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedCacheRepository(val description: String = "")
```

Applied to a cache access class (e.g. a Redis-backed cache repository). Instruments every method as a **CACHE-layer** child span — same mechanics as `@ManagedRepository`, but rendered as `[CAC]` instead of `[DB ]` so cache traffic is distinguishable from database traffic in span trees and metrics.

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

### `@ManagedSchedule`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedSchedule(
    val description: String = "",
    val quietWhenEmpty: Boolean = false
)
```

Applied to a scheduler-triggered method (e.g. `@Scheduled`). Scheduled executions have no incoming request or message to carry trace context, so this opens an **ENTRY-layer** span with a freshly generated `traceId`/`causationId`, sets `executionScope` to `SCHEDULED`, and sets `protocol`/`type` to `SCHEDULED`.

| Parameter        | Type      | Default | Description               |
|------------------|-----------|---------|---------------------------|
| `description`    | `String`  | `""`    | Human-readable description |
| `quietWhenEmpty` | `Boolean` | `false` | Silences the default success log when the method returns an "empty" result (null, `Unit`, `0`, `false`, or an empty Collection/Map/Array/CharSequence). Return the processed count or batch from a high-frequency poller so only meaningful runs are logged. Failures are always logged; spans and metrics are recorded either way |

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
| `ip`             | `String`              | Client IP (`X-Forwarded-For` first hop, else remote address) |
| `deviceId`       | `String`              | `"NOT_SUPPORTED_YET"` — reserved, not populated yet       |
| `deviceInfo`     | `String`              | `"NOT_SUPPORTED_YET"` — reserved, not populated yet       |
| `protocol`       | `ManagedProtocolType` | `HTTP`, `MESSAGING`, `RPC`, `DB`, etc.                   |
| `type`           | `String`              | Operation type: `"API"`, `"EVENT"`, `"BATCH"`, etc.      |
| `uri`            | `String`              | HTTP request URI                                         |
| `method`         | `String`              | HTTP method (`"GET"`, `"POST"`, etc.)                    |
| `entrypoint`     | `String`              | Controller class name                                    |
| `service`        | `String`              | Service class name                                       |
| `operation`      | `String`              | `@ManagedOperation.operation` value                      |
| `useCase`        | `String`              | `@ManagedOperation.useCase` value                        |
| `response`       | `String`              | `toString()` of the `Operations { }` return value        |
| `statusCode`     | `Int?`                | HTTP response status code; `null` until injected at response commit |
| `outcome`        | `OperationOutcome`    | Classification derived from `statusCode`; `SUCCESS` until injected |
| `timestamp`      | `Instant`             | Context creation time (UTC)                              |
| `durationMs`     | `Long`                | Total execution time in milliseconds                     |
| `executionScope` | `ExecutionScope`      | `PRIMARY`, `ASYNC`, or `EVENT`                           |
| `rootSpan`       | `MetricSpan?`         | Root of the span tree; `null` until first span is pushed |
| `hookRecords`    | `List<HookRecord>`    | Execution results of each hook                           |
| `capturedException` | `Throwable?`       | Real exception recorded before a `@ExceptionHandler`/`@ControllerAdvice` converted it into a response — via `ExceptionCapturingResolver` (webmvc) or `ManagedControllerAspect` (webflux); `null` if none was caught. First exception wins |

### Mutable fields

| Field                | Type      | Description                                                                      |
|----------------------|-----------|----------------------------------------------------------------------------------|
| `message`            | `String`  | Free-form label; set in a hook before `DefaultOperationLoggingHook` (Order < 50) |
| `defaultLogging`     | `Boolean` | Set to `false` inside an `Operations { }` block to silence the default success log for this execution only; failures, client errors, and captured exceptions are still logged. Useful for noisy paths such as frequent `@ManagedSchedule` jobs (e.g. `defaultLogging = processed > 0`) |
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
| `context`    | `ManagedContext` | Current context. Outside a managed scope it logs a WARN and returns a detached (unmanaged) context instead of failing — spans/hooks are not recorded for that execution |
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

`onFailure` fires only when the response status is `5xx` (or the handler throws). All other responses — including `401`/`403`/other `4xx` — call `onSuccess`; inspect `context.outcome` (see [OperationOutcome](#operationoutcome)) to distinguish these from a true `SUCCESS`.

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

| Hook                          | Order | Description                            |
|-------------------------------|-------|----------------------------------------|
| `DefaultOperationLoggingHook` | 50    | Logs operation result (pretty / JSON)  |
| `MetricsOperationHook`        | 60    | Records span tree to Micrometer        |

---

## SpanBridge (OpenTelemetry live bridge)

OpenTelemetry integration is **not** a hook: when a `Tracer` bean is present, a live
`SpanBridge` is attached to the `OperationRuntime`. Every `ManagedContext.push()` then starts
a **real OTel span at that moment** and adopts the OTel-generated ids back into OMK — the
`spanId`/`traceId` you see in OMK logs are the ids you search for in the trace viewer.

- Incoming `traceparent` (W3C mode) continues the upstream trace; without one, the
  OTel-generated traceId is adopted into the context.
- Servlet: the span is also installed as the OTel *current context* per thread, so OTel
  auto-instrumented clients (JDBC, `RestClient`, ...) nest under OMK spans.
- Reactive: the span's OTel context travels in the Reactor context
  (`opentelemetry-reactor-3.1` optional), so instrumented reactive clients
  (`WebClient`, R2DBC, ...) nest under OMK spans.
- No `Tracer` bean or no OTel on the classpath → no bridge; OMK generates its own ids and
  runs fully self-contained. Bridge errors never break business flow (fail-open).

```kotlin
interface SpanBridge {
    fun startTrace(context: ManagedContext): BridgedTrace
    fun startSpan(trace: BridgedTrace, name: String, layer: MetricLayer,
                  tags: MetricTags, parent: BridgedSpan?): BridgedSpan
    fun endSpan(handle: BridgedSpan, span: MetricSpan)
}
```

The OTel implementation lives in the `otel` module (`OtelSpanBridge`) and is auto-configured
by the servlet/reactive starters; application code normally never touches this interface.

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
| `descriptor.layer` | `MetricLayer`      | `ENTRY`, `APPLICATION`, `DB`, `CACHE`, or `EXTERNAL`     |
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
    PRIMARY,   // Main HTTP / event loop thread
    ASYNC,     // @Async thread or forked coroutine
    EVENT,     // @ManagedEventHandler
    SCHEDULED  // @ManagedSchedule
}
```

---

## ManagedProtocolType

```kotlin
enum class ManagedProtocolType {
    HTTP, RPC, MESSAGING, SCHEDULED, FAAS, DB, UNSUPPORTED
}
```

---

## OperationOutcome

```kotlin
enum class OperationOutcome {
    SUCCESS, UNAUTHENTICATED, FORBIDDEN, CLIENT_ERROR, SERVER_ERROR
}
```

Derived from the HTTP response status code via `ManagedContext.injectStatusCode(statusCode)`, called by the WebMVC/WebFlux filters right before the success/failure hooks fire.

| Outcome           | Status code range |
|-------------------|--------------------|
| `SUCCESS`         | < 400              |
| `UNAUTHENTICATED` | 401                |
| `FORBIDDEN`       | 403                |
| `CLIENT_ERROR`    | other 4xx          |
| `SERVER_ERROR`    | >= 500             |

`onFailure` is only triggered for `SERVER_ERROR`; all other outcomes (including `UNAUTHENTICATED` / `FORBIDDEN`) still call `onSuccess`, since the request was handled and a response was returned as intended (e.g. Spring Security's `AuthenticationEntryPoint` writing a 401). `DefaultOperationLoggingHook` reads `context.outcome` inside `onSuccess` to log non-`SUCCESS` outcomes at `logging.client-error-level` instead of `logging.success-level`.

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

### WebMVC — `operation-manager.servlet`

| Property                                            | Type              | Default          | Description                                 |
|-----------------------------------------------------|-------------------|------------------|---------------------------------------------|
| `context-filter.enabled`                            | `Boolean`         | `true`           | Enable servlet filter                       |
| `context-aspect.enabled`                            | `Boolean`         | `true`           | Enable AOP aspects                          |
| `micrometer.enabled`                                | `Boolean`         | `true`           | Enable Micrometer recording                 |
| `otel.enabled`                                      | `Boolean`         | `true`           | Enable the live OTel span bridge (requires `Tracer` bean) |
| `logging.enabled`                                   | `Boolean`         | `true`           | Enable default logging hook                 |
| `logging.pretty`                                    | `Boolean`         | `false`          | Pretty-print format                         |
| `logging.json`                                      | `Boolean`         | `true`           | JSON format                                 |
| `logging.spans`                                     | `Boolean`         | `false`          | Include span tree in pretty output          |
| `logging.response`                                  | `Boolean`         | `true`           | Include operation return value in log       |
| `logging.success-level`                             | `LogLevel`        | `INFO`           | Log level for successful operations         |
| `logging.failure-level`                             | `LogLevel`        | `ERROR`          | Log level for failed operations             |
| `logging.client-error-level`                        | `LogLevel`        | `WARN`           | Log level for `onSuccess` calls whose `outcome` is not `SUCCESS` (e.g. `UNAUTHENTICATED`, `FORBIDDEN`, `CLIENT_ERROR`) |
| `async-propagation.enabled`                         | `Boolean`         | `true`           | Enable `@Async` context propagation         |
| `async-propagation.hook-enabled`                    | `Boolean`         | `false`          | Run hooks on async task completion          |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` or `CUSTOM`                  |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | Generate IDs when not in request headers    |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | Custom trace ID header name                 |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | Custom causation ID header name             |

### WebFlux — `operation-manager.reactive`

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
| `logging.client-error-level`                        | `LogLevel`        | `WARN`           | Log level for `onSuccess` calls whose `outcome` is not `SUCCESS` (e.g. `UNAUTHENTICATED`, `FORBIDDEN`, `CLIENT_ERROR`) |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` or `CUSTOM`               |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | Generate IDs when not in request headers |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | Custom trace ID header name              |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | Custom causation ID header name          |

---