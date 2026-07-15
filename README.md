# Operation Manager Kit

[![JitPack](https://jitpack.io/v/Hchanjune/operation-manager-kit.svg)](https://jitpack.io/#Hchanjune/operation-manager-kit)
[![codecov](https://codecov.io/gh/Hchanjune/operation-manager-kit/branch/main/graph/badge.svg)](https://codecov.io/gh/Hchanjune/operation-manager-kit)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple?logo=kotlin&logoColor=white)

**[한국어](README.ko.md) | English**

---

## Table of Contents

- [Why OMK?](#why-omk)
- [Modules](#modules)
- [Compatibility](#compatibility)
- [Installation](#installation)
  - [Servlet Stack (Spring WebMVC)](#servlet-stack-servlet)
  - [Reactive Stack (Spring WebFlux + Kotlin Coroutines)](#reactive-stack-reactive--kotlin-coroutines)
- [Usage at a Glance](#usage-at-a-glance)
- [Roadmap](#roadmap)
- [License](#license)

---

**Operation Manager Kit** is a lightweight observability and distributed tracing library for Spring-based applications.

It provides a structured execution boundary around business logic, automatically capturing consistent metadata such as trace IDs, issuer identity, HTTP context, service/operation names, execution timing, and lifecycle hooks — with minimal configuration.

---

## Why OMK?

Spring Boot already ships an observability stack — the Observation API (`@Observed`) with
Micrometer Tracing. OMK is **not a replacement** for it: it layers an *operation-centric*
model on top and bridges into the same backends (Micrometer, OpenTelemetry).

The standard stack answers *"what happened at this instrumentation point?"* — independent
spans and timers, correlated later by trace id in the backend. OMK answers
*"what happened in this operation?"* — every request, event, or scheduled run produces
**one structured log document** carrying the full span tree, timings, business context, and
outcome:

```
┌───────────────────────────────────────────────────────────────────────────────────
│ ✅ Success
├─ Status      : SUCCESS
├─ TraceId     : 4bf92f3577b34da6a3ce929d0e0e4736
├─ Issuer      : user-1042
├─ Entrypoint  : OrderController
├─ Operation   : CreateOrder
├─ UseCase     : PlaceOrder
├─ Performance : 56Ms
├─ Spans      :
│    [ENT] 12:34:56.733  [nio-8080-exec-1]  OrderController.create   [56ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [nio-8080-exec-1]  CreateOrder      [48ms]   SUCCESS
│                   └─ [DB ] 12:34:56.751  [nio-8080-exec-1]  OrderRepository.save  [18ms]  SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

What that buys you over the standard stack alone:

| | Spring Observability (`@Observed`) | OMK |
|---|---|---|
| Unit of observation | Each instrumentation point, independently | One **operation** (request / event / schedule) with its span tree |
| Log output | One line per event; correlate by trace id in the backend | **One structured log per operation** (JSON or pretty box) — greppable/queryable without a trace backend |
| Business metadata | Free-form key-value tags | First-class fields: `operation`, `useCase`, `issuer`, `entrypoint`, `ip`, and outcome classification (`SUCCESS` / `CLIENT_ERROR` / `UNAUTHENTICATED` / ...) |
| Exception visibility | Lost if `@ControllerAdvice` converts it first | Captured **before** the advice turns it into a response |
| Messaging / schedulers | Manual context propagation | `@ManagedEventHandler` extracts trace context from event fields; `@ManagedSchedule` opens a fresh trace (+ `quietWhenEmpty` for high-frequency pollers) |
| OpenTelemetry | Native | Live bridge — OMK spans **are** OTel spans with adopted ids (logs and Tempo/Jaeger share the same `spanId`); auto-instrumented clients nest under OMK spans |

**When the standard stack is enough:** if you only need traces and metrics and are happy
correlating logs in the trace backend, stay with plain Spring observability. Reach for OMK
when you want the whole operation — spans, timings, business context, outcome — in one
queryable log document, with tracing and metrics coming along for free.

---

## Modules

| Module           | Description                                                                                   | Docs                       |
|------------------|-----------------------------------------------------------------------------------------------|----------------------------|
| `core`           | Framework-agnostic execution engine, context model, and provider contracts                    | —                          |
| `otel`           | Live OpenTelemetry span bridge (`OtelSpanBridge`) — pulled in transitively by the stack modules | —                        |
| `servlet`  | Spring Boot auto-configuration for Servlet stack (AOP aspects, servlet filters, Micrometer)   | [Servlet.md](Servlet.md)   |
| `reactive` | Spring Boot auto-configuration for Reactive stack (AOP aspects, WebFilter, Kotlin Coroutines) | [Reactive.md](Reactive.md) |

→ **[API Reference](API.md)** — All annotations, classes, interfaces, and configuration properties

---

## Compatibility

| Spring Boot   | Spring Framework | Java | Status                              |
|---------------|------------------|------|-------------------------------------|
| 3.2.x         | 6.1              | 17+  | Supported (minimum)                 |
| 3.3.x – 3.5.x | 6.1 / 6.2        | 17+  | Supported                           |
| 4.0.x+        | 7.0+             | 21+  | Supported                           |
| 2.x           | 5.x              | —    | Not supported (`javax.*` namespace) |

> **Spring Boot 2.x** is not supported due to the `javax.*` → `jakarta.*` namespace migration in Spring Boot 3.0.

---

## Installation

Add JitPack to your repositories:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Then include the modules for your stack:

### Servlet Stack (Spring WebMVC)

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:servlet:x.x.x")

    // AOP support
    implementation("org.springframework.boot:spring-boot-starter-aop")         // Spring Boot 3.x
    // implementation("org.aspectj:aspectjweaver")                              // Spring Boot 4.x
}
```

→ See **[Servlet.md](Servlet.md)** for full setup and usage.

### Reactive Stack (Spring WebFlux + Kotlin Coroutines)

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:reactive:x.x.x")

    // AOP and Micrometer support
    implementation("org.aspectj:aspectjweaver")
    implementation("io.micrometer:micrometer-core")

    // kotlin-reflect must match kotlin-stdlib version
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

→ See **[Reactive.md](Reactive.md)** for full setup and usage.

---

## Usage at a Glance

### Servlet Stack (Spring WebMVC)

Annotate your components and wrap the business logic in `Operations { }` to capture the execution result alongside its managed context:

```kotlin
@Service
@ManagedService
class OrderService(private val orderRepository: OrderRepository) {

    @ManagedOperation(operation = "CreateOrder", useCase = "OrderManagement")
    fun create(request: OrderRequest) = Operations {
        orderRepository.save(Order.from(request))
    }
}
```

### Reactive Stack (Spring WebFlux + Kotlin Coroutines)

Use `ReactiveOperations { }` inside `suspend fun` methods. For non-coroutine Mono-returning methods, use `ReactiveOperations.mono { }`:

```kotlin
@Service
@ManagedService
class OrderService(private val orderRepository: OrderRepository) {

    // suspend fun — recommended
    @ManagedOperation(operation = "CreateOrder", useCase = "OrderManagement")
    suspend fun create(request: OrderRequest) = ReactiveOperations {
        orderRepository.save(Order.from(request))
    }

    // Mono-returning method
    @ManagedOperation(operation = "FindOrder", useCase = "OrderManagement")
    fun find(id: String): Mono<OperationResult<Order>> = ReactiveOperations.mono {
        orderRepository.findById(id)
    }
}
```

| | Servlet (WebMVC) | Reactive (WebFlux) |
|---|---|---|
| Coroutine / blocking | `Operations { }` | `ReactiveOperations { }` |
| Mono-returning | — | `ReactiveOperations.mono { }` |

---

## Roadmap

- [ ] Maven Central publishing
- [x] Spring WebMVC support
- [x] Spring WebFlux support (Kotlin Coroutines)
- [x] Spring WebMVC Async context propagation (`@Async`, coroutines, virtual threads)
- [x] Span-level metric instrumentation
- [x] Micrometer integration
- [x] OpenTelemetry live span bridge — OMK spans are real OTel spans with adopted ids (logs and trace viewer share the same `spanId`/`traceId`); auto-instrumented clients nest under OMK spans
- [x] Messaging context propagation (`@ManagedEventHandler`) — `handle()` must be a plain (non-suspend) `fun`. If the body calls suspend functions, wrap them in `runBlocking { }` inside the implementation.
- [x] Scheduler context creation (`@ManagedSchedule`) — opens a fresh trace context for scheduler-triggered methods (e.g. `@Scheduled`) that have no incoming request or message.

---

## License

### [Apache License 2.0](LICENSE)

---
