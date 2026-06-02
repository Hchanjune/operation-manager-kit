# Operation Manager Kit

[![JitPack](https://jitpack.io/v/Hchanjune/operation-manager-kit.svg)](https://jitpack.io/#Hchanjune/operation-manager-kit)
[![codecov](https://codecov.io/gh/Hchanjune/operation-manager-kit/branch/main/graph/badge.svg)](https://codecov.io/gh/Hchanjune/operation-manager-kit)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple?logo=kotlin&logoColor=white)

**[한국어](README.ko.md) | English**

---

**Operation Manager Kit** is a lightweight observability and distributed tracing library for Spring-based applications.

It provides a structured execution boundary around business logic, automatically capturing consistent metadata such as trace IDs, issuer identity, HTTP context, service/operation names, execution timing, and lifecycle hooks — with minimal configuration.

---

## Modules

| Module | Description | Docs |
|--------|-------------|------|
| `core` | Framework-agnostic execution engine, context model, and provider contracts | — |
| `spring-webmvc` | Spring Boot auto-configuration for Servlet stack (AOP aspects, servlet filters, Micrometer) | [Servlet.md](Servlet.md) |
| `spring-webflux` | Spring Boot auto-configuration for Reactive stack (AOP aspects, WebFilter, Kotlin Coroutines) | [Reactive.md](Reactive.md) |

---

## Compatibility

| Spring Boot | Spring Framework | Java | Status |
|-------------|-----------------|------|--------|
| 3.2.x | 6.1 | 17+ | Supported (minimum) |
| 3.3.x – 3.5.x | 6.1 / 6.2 | 17+ | Supported |
| 4.0.x+ | 7.0+ | 21+ | Supported |
| 2.x | 5.x | — | Not supported (`javax.*` namespace) |

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
    implementation("com.github.Hchanjune.operation-manager-kit:core:x.x.x")
    implementation("com.github.Hchanjune.operation-manager-kit:spring-webmvc:x.x.x")

    // AOP support
    implementation("org.springframework.boot:spring-boot-starter-aop")         // Spring Boot 3.x
    // implementation("org.aspectj:aspectjweaver")                              // Spring Boot 4.x
}
```

→ See **[Servlet.md](Servlet.md)** for full setup and usage.

### Reactive Stack (Spring WebFlux + Kotlin Coroutines)

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:core:x.x.x")
    implementation("com.github.Hchanjune.operation-manager-kit:spring-webflux:x.x.x")

    // AOP and Micrometer support
    implementation("org.aspectj:aspectjweaver")
    implementation("io.micrometer:micrometer-core")

    // kotlin-reflect must match kotlin-stdlib version
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

→ See **[Reactive.md](Reactive.md)** for full setup and usage.

---

## Roadmap

- [ ] Maven Central publishing
- [x] Spring WebMVC support
- [x] Spring WebFlux support (Kotlin Coroutines)
- [x] Spring WebMVC Async context propagation (`@Async`, coroutines, virtual threads)
- [x] Span-level metric instrumentation
- [x] Micrometer integration
- [x] OpenTelemetry SDK integration
- [x] Messaging context propagation (`@ManagedEventHandler`)

---

## License

This project is open-source. Contributions are welcome.
