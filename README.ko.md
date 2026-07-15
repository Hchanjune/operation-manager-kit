# Operation Manager Kit

[![JitPack](https://jitpack.io/v/Hchanjune/operation-manager-kit.svg)](https://jitpack.io/#Hchanjune/operation-manager-kit)
[![codecov](https://codecov.io/gh/Hchanjune/operation-manager-kit/branch/main/graph/badge.svg)](https://codecov.io/gh/Hchanjune/operation-manager-kit)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple?logo=kotlin&logoColor=white)

**한국어 | [English](README.md)**

---

## 목차

- [모듈](#모듈)
- [호환성](#호환성)
- [설치](#설치)
  - [Servlet 스택 (Spring WebMVC)](#servlet-스택-servlet)
  - [Reactive 스택 (Spring WebFlux + Kotlin Coroutines)](#reactive-스택-reactive--kotlin-coroutines)
- [한눈에 보는 사용법](#한눈에-보는-사용법)
- [로드맵](#로드맵)
- [라이선스](#라이선스)

---

**Operation Manager Kit**은 Spring 기반 애플리케이션을 위한 경량 관찰가능성(Observability) 및 분산 추적 라이브러리입니다.

비즈니스 로직 주변에 구조화된 실행 경계를 제공하며, 트레이스 ID, 발급자 정보, HTTP 컨텍스트, 서비스/오퍼레이션명, 실행 시간, 라이프사이클 훅 등의 메타데이터를 최소한의 설정으로 자동 수집합니다.

---

## 모듈

| 모듈               | 설명                                                           | 문서                               |
|------------------|--------------------------------------------------------------|----------------------------------|
| `core`           | 프레임워크 독립적인 실행 엔진, 컨텍스트 모델, 프로바이더 계약                          | —                                |
| `otel`           | 라이브 OpenTelemetry span 브릿지 (`OtelSpanBridge`) — 스택 모듈이 전이 포함  | —                                |
| `servlet`  | Servlet 스택 자동 설정 (AOP Aspect, 서블릿 필터, Micrometer)            | [Servlet.ko.md](Servlet.ko.md)   |
| `reactive` | Reactive 스택 자동 설정 (AOP Aspect, WebFilter, Kotlin Coroutines) | [Reactive.ko.md](Reactive.ko.md) |

→ **[API 레퍼런스](API.ko.md)** — 모든 어노테이션, 클래스, 인터페이스, 설정 프로퍼티

---

## 호환성

| Spring Boot   | Spring Framework | Java | 지원 여부                  |
|---------------|------------------|------|------------------------|
| 3.2.x         | 6.1              | 17+  | 지원 (최소 버전)             |
| 3.3.x – 3.5.x | 6.1 / 6.2        | 17+  | 지원                     |
| 4.0.x+        | 7.0+             | 21+  | 지원                     |
| 2.x           | 5.x              | —    | 미지원 (`javax.*` 네임스페이스) |

> **Spring Boot 2.x**는 Spring Boot 3.0에서 `javax.*` → `jakarta.*` 네임스페이스가 변경되었기 때문에 지원하지 않습니다.

---

## 설치

JitPack 저장소를 추가하세요:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

사용하는 스택에 맞는 모듈을 추가하세요:

### Servlet 스택 (Spring WebMVC)

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:servlet:x.x.x")

    // AOP 지원
    implementation("org.springframework.boot:spring-boot-starter-aop")         // Spring Boot 3.x
    // implementation("org.aspectj:aspectjweaver")                              // Spring Boot 4.x
}
```

→ 전체 설정 및 사용법은 **[Servlet.ko.md](Servlet.ko.md)** 를 참고하세요.

### Reactive 스택 (Spring WebFlux + Kotlin Coroutines)

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:reactive:x.x.x")

    // AOP 및 Micrometer 지원
    implementation("org.aspectj:aspectjweaver")
    implementation("io.micrometer:micrometer-core")

    // kotlin-reflect 버전이 kotlin-stdlib 버전과 일치해야 함
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

→ 전체 설정 및 사용법은 **[Reactive.ko.md](Reactive.ko.md)** 를 참고하세요.

---

## 한눈에 보는 사용법

### Servlet 스택 (Spring WebMVC)

컴포넌트에 어노테이션을 붙이고, 비즈니스 로직을 `Operations { }` 로 감싸면 실행 결과와 관리 컨텍스트를 함께 캡처합니다:

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

### Reactive 스택 (Spring WebFlux + Kotlin Coroutines)

`suspend fun` 안에서는 `ReactiveOperations { }` 를 사용합니다. Mono를 반환하는 비코루틴 메서드에는 `ReactiveOperations.mono { }` 를 사용합니다:

```kotlin
@Service
@ManagedService
class OrderService(private val orderRepository: OrderRepository) {

    // suspend fun — 권장 패턴
    @ManagedOperation(operation = "CreateOrder", useCase = "OrderManagement")
    suspend fun create(request: OrderRequest) = ReactiveOperations {
        orderRepository.save(Order.from(request))
    }

    // Mono 반환 메서드
    @ManagedOperation(operation = "FindOrder", useCase = "OrderManagement")
    fun find(id: String): Mono<OperationResult<Order>> = ReactiveOperations.mono {
        orderRepository.findById(id)
    }
}
```

| | Servlet (WebMVC) | Reactive (WebFlux) |
|---|---|---|
| 코루틴 / 블로킹 | `Operations { }` | `ReactiveOperations { }` |
| Mono 반환 | — | `ReactiveOperations.mono { }` |

---

## 로드맵

- [ ] Maven Central 배포
- [x] Spring WebMVC 지원
- [x] Spring WebFlux 지원 (Kotlin Coroutines)
- [x] Spring WebMVC 비동기 컨텍스트 전파 (`@Async`, 코루틴, 가상 스레드)
- [x] Span 수준 메트릭 계측
- [x] Micrometer 연동
- [x] OpenTelemetry 라이브 span 브릿지 — OMK span이 곧 진짜 OTel span (id 채택으로 로그와 트레이스 뷰어가 같은 `spanId`/`traceId` 공유), 자동계측 클라이언트가 OMK span 아래 중첩
- [x] 메시징 컨텍스트 전파 (`@ManagedEventHandler`) — `handle()`은 반드시 일반(non-suspend) `fun`이어야 함. 내부에서 suspend 함수를 호출할 경우 구현체 내부에서 `runBlocking { }`으로 감쌀 것.
- [x] 스케줄러 컨텍스트 생성 (`@ManagedSchedule`) — 요청/메시지가 없는 스케줄러 실행(예: `@Scheduled`)에 새 트레이스 컨텍스트를 생성.

---

## 라이선스

### [Apache License 2.0](LICENSE)

---