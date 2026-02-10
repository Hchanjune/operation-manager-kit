# Operation Manager Kit

**OperationManagerKit** is a lightweight operation execution and tracing toolkit for Spring-based applications.

It provides a structured execution boundary around business logic, capturing consistent metadata such as:

- Correlation ID (request/operation trace identifier)
- Issuer (actor identity)
- Entrypoint (controller method)
- Service / Function invocation
- Execution duration
- Success / Failure lifecycle hooks

The goal is to make application operations **observable, auditable, and traceable** with minimal effort.

---

## Modules

| Module | Description |
|--------|-------------|
| `operation-manager-kit-core` | Core execution engine and contracts |
| `operation-manager-kit-webmvc` | Spring MVC integration (Interceptor + MDC + AOP Aspect) |

---

## Installation

### Gradle (JitPack)

Until the library is published to Maven Central, you can use **JitPack**:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:core:main-SNAPSHOT")
    implementation("com.github.Hchanjune.operation-manager-kit:spring-webmvc:main-SNAPSHOT")
}
```

## Quick Start
### 1. Mark operations with @OperationManaged

OperationManagerKit uses an opt-in model.

Only methods or classes annotated with @OperationManaged will be captured by AOP.

```kotlin
import io.github.hchanjune.operationresult.core.annotations.OperationManaged
import org.springframework.stereotype.Service

@Service
class MyService {

    @OperationManaged
    fun create() = Operations {
        ...
    }
}
```

You can also annotate the entire class:

```kotlin
@OperationManaged
@Service
class MyService {
    fun create() = Operations { ... }
    fun delete() = Operations { ... }
}
```

### 2. Execute operations via Operations

```kotlin
import io.github.hchanjune.operationresult.core.Operations

val result = Operations {
    // business logic
    "OK"
}

println(result.context.correlationId)
println(result.data)
```

or

```kotlin
import io.github.hchanjune.operationresult.core.Operations

@OperationManaged
fun myMethod(event: MyEvent): String = Operations {
    // business logic
    "OK"
} 

println(result.context.correlationId) // => context info
println(result.data) // => "OK"
```

# Spring MVC Integration

When using operation-manager-kit-webmvc, the library automatically provides:

### Entrypoint capture (Interceptor)

- Captures controller entrypoint into MDC:

```yaml
entrypoint = MyController#create
```

### Service/function capture (Aspect)

For @OperationManaged execution points:

```yaml
service   = MyService
function  = create
```

### MDC-backed InvocationInfoProvider

Invocation metadata is automatically converted into InvocationInfo and stored into OperationContext.

## Configuration Properties

### Disable MVC interceptor

```yaml
operationresult:
  webmvc:
    mdc-entrypoint-interceptor:
      enabled: false
```

### Disable Service Aspect

```yaml
operationresult:
  webmvc:
    mdc-service-aspect:
      enabled: false
```

## Issuer Resolution (Spring Security Optional)

If Spring Security is present, the library automatically resolves:

```yaml
issuer = authentication.name
```

If not, it falls back to:

```yaml
issuer = "anonymous"
```

Spring Security is an optional dependency.

## Hooks

You can observe operation lifecycle events by providing a custom OperationHooks bean:

```kotlin
@Component
class LoggingHooks : OperationHooks {

    override fun onSuccess(context: OperationContext) {
        println("SUCCESS: ${context.service}#${context.function}")
    }

    override fun onFailure(context: OperationContext, exception: Throwable) {
        println("FAILURE: ${exception.message}")
    }
}
```

# Notes & Limitations
- MDC is thread-local: async execution may require MDC propagation.
- Spring AOP does not intercept self-invocation (internal method calls).
- response is currently recorded via toString() and should not contain sensitive data.

# Roadmap
- Maven Central publishing
- WebFlux support
- OpenTelemetry integration
- Configurable MDC key prefix
- Sampling support for high-throughput environments