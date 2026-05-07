# Operation Manager Kit

[![JitPack](https://jitpack.io/v/Hchanjune/operation-manager-kit.svg)](https://jitpack.io/#Hchanjune/operation-manager-kit)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-brightgreen)
![Java](https://img.shields.io/badge/Java-21-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple)

**한국어 | [English](README.md)**

---

**Operation Manager Kit**은 Spring 기반 애플리케이션을 위한 경량 관찰가능성(Observability) 및 분산 추적 라이브러리입니다.

비즈니스 로직 주변에 구조화된 실행 경계를 제공하며, 트레이스 ID, 발급자 정보, HTTP 컨텍스트, 서비스/오퍼레이션명, 실행 시간, 라이프사이클 훅 등의 메타데이터를 최소한의 설정으로 자동 수집합니다.

---

## 모듈

| 모듈 | 설명 |
|------|------|
| `core` | 프레임워크 독립적인 실행 엔진, 컨텍스트 모델, 프로바이더 계약 |
| `spring-webmvc` | Spring Boot 자동 설정, AOP Aspect, 서블릿 필터, Micrometer 연동 |

---

## 설치

JitPack 저장소를 추가하고 필요한 모듈을 의존성에 포함하세요.

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

## 빠른 시작

### 1. 컴포넌트에 어노테이션 붙이기

컨트롤러, 서비스, 리포지토리에 어노테이션을 붙이면 자동으로 컨텍스트가 채워집니다.

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

### 2. Operations로 컨텍스트 접근

관리 범위 내 어디서든 현재 요청 컨텍스트에 접근할 수 있습니다.

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

## 어노테이션

| 어노테이션 | 대상 | 역할 |
|------------|------|------|
| `@ManagedController` | 클래스 | 컨트롤러 클래스명을 컨텍스트의 entrypoint에 주입 |
| `@ManagedService` | 클래스 | 서비스 클래스명을 컨텍스트의 service에 주입 |
| `@ManagedRepository` | 클래스 | 리포지토리 메트릭 계측 예정 |
| `@ManagedOperation` | 메서드 | `operation`, `useCase` 값을 컨텍스트에 주입 |

> 모든 어노테이션은 클래스 수준의 저 카디널리티 식별자만 주입합니다. 메트릭 카디널리티 폭발을 방지하기 위해 메서드명은 의도적으로 제외됩니다.

---

## 훅 시스템

훅을 통해 오퍼레이션 라이프사이클 이벤트(성공 또는 실패)에 반응할 수 있습니다.

### 훅 순서 (Hook Ordering)

훅은 Spring의 `@Order` 어노테이션으로 순서가 결정됩니다. 내장 `DefaultOperationLoggingHook`은 **Order 50**으로 등록되며, 이를 기준점으로 삼습니다.

```
Order < 50  →  전처리 훅  (컨텍스트 보강, 사전 처리)
Order 50    →  DefaultOperationLoggingHook  (보강된 컨텍스트를 로그로 출력)
Order > 50  →  후처리 훅  (메트릭 기록, 알림 등)
```

로깅 훅보다 **앞에** 등록된 훅(Order < 50)의 실행 결과는 로그 출력에 포함됩니다.

### 컨텍스트의 훅 실행 결과

각 훅의 실행 결과는 실행 후 `ManagedContext.hookRecords`에 자동으로 기록됩니다.

```kotlin
context.hookRecords.forEach { record ->
    println("${record.hookName}: ${if (record.success) "OK" else "FAIL"}")
}
```

훅이 예외를 던지더라도 **격리**되어 이후 훅은 계속 실행됩니다. `CompositeOperationHook`은 각 실패에 대해 경고 로그를 출력합니다.

### 커스텀 훅 예시

```kotlin
@Component
@Order(30) // DefaultOperationLoggingHook 이전에 실행
class MyEnrichmentHook : OperationHook {

    override fun onSuccess(context: ManagedContext) {
        context.message = "정상 처리 완료"
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        context.message = "처리 실패: ${exception.message}"
    }
}
```

### 기본 로깅 훅

내장 `DefaultOperationLoggingHook`은 pretty-print와 JSON 두 가지 로그 포맷을 지원하며, 설정으로 제어할 수 있습니다.

**Pretty 출력 예시:**
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

## 분산 추적 (Distributed Tracing)

기본적으로 **W3C Trace Context** 표준을 사용하여 서비스 간 트레이스 컨텍스트를 전파합니다.

### W3C 표준 모드 (기본값)

수신된 `traceparent` 헤더를 파싱하고, 응답에 업데이트된 `traceparent`를 포함합니다.

```
traceparent: 00-{traceId 32hex}-{spanId 16hex}-01
```

응답 `traceparent`는 요청 처리가 **완료된 후** 주입되므로, 실제 rootSpan ID를 항상 올바르게 반영합니다.

### 커스텀 헤더 모드

`traceparent` 대신 커스텀 헤더를 사용할 수 있습니다.

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

## 설정 프로퍼티

모든 설정은 `operation-manager.webmvc` 접두사 하위에 있습니다.

```yaml
operation-manager:
  webmvc:
    context-filter:
      enabled: true       # 요청마다 ManagedContext를 생성하는 서블릿 필터 활성화 (기본값: true)

    context-aspect:
      enabled: true       # @ManagedController, @ManagedService 등 AOP Aspect 활성화 (기본값: true)

    micrometer:
      enabled: true       # Micrometer 메트릭 기록 활성화 (기본값: true)

    logging:
      enabled: true
      pretty: false       # Pretty-print 포맷 (사람이 읽기 쉬운 형태)
      json: true          # JSON 포맷 (프로덕션 권장)
      success-level: INFO
      failure-level: ERROR

    async-propagation:
      enabled: true       # @Async 컨텍스트 전파 활성화 (기본값: true)

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## `@Async` 컨텍스트 전파

`@EnableAsync`가 활성화된 경우, `ManagedContextTaskDecorator`가 자동으로 등록되어 기본 `ThreadPoolTaskExecutor`에 적용됩니다. 별도 설정 없이 관리 컨텍스트(트레이스 ID, 발급자, 오퍼레이션 메타데이터)가 async 스레드로 전파됩니다.

```kotlin
@Service
class NotificationService {

    @Async
    fun sendEmail(to: String) {
        val traceId = Operations.context.traceId  // 호출 스레드로부터 상속
        log.info("[{}] {} 로 메일 발송", traceId, to)
    }
}
```

**전파되는 것:** `traceId`, `issuer`, `entrypoint`, `service`, `operation`, `useCase` 및 모든 컨텍스트 필드.

**전파되지 않는 것:** 훅 실행 및 로깅 라이프사이클. 훅은 HTTP 요청이 완료될 때 메인 스레드에서 실행됩니다. 요청보다 오래 살아있는 async 태스크는 설계상 훅 라이프사이클 밖에 있습니다 — `@Async`는 메인 요청 흐름의 연속이 아닌 사이드이펙트 처리 메커니즘이기 때문입니다.

비활성화하려면:

```yaml
operation-manager:
  webmvc:
    async-propagation:
      enabled: false
```

> 커스텀 `AsyncConfigurer` 또는 커스텀 `ThreadPoolTaskExecutor`를 사용하는 경우, `ManagedContextTaskDecorator`를 직접 주입해서 수동으로 적용하세요.

---

## Kotlin 코루틴 컨텍스트 전파

`kotlinx-coroutines-core`가 클래스패스에 있으면, `ManagedContextElement`를 사용하여 코루틴 suspension point와 스레드 전환 사이에서 관리 컨텍스트를 전파할 수 있습니다.

`TaskDecorator`가 태스크 제출 시점에만 컨텍스트를 캡처하는 것과 달리, `ManagedContextElement`는 코루틴 dispatcher에 훅으로 연결됩니다 — 모든 suspension point 이후를 포함해 코루틴이 실행되는 모든 스레드에서 `ThreadLocal` 컨텍스트를 자동으로 복원합니다.

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    withContext(Dispatchers.Default) {
        Operations.context.traceId  // 모든 suspension point에서 접근 가능
    }
}
```

### 사용 패턴별 동작

**Structured concurrency — 컨텍스트와 span이 전파됨**

자식 코루틴이 요청 완료 전에 await되는 경우, 훅 실행 시점에 해당 작업 결과가 반영됩니다.

```kotlin
@ManagedService
@Service
class OrderService {

    @ManagedOperation(operation = "PlaceOrder")
    suspend fun place(request: OrderRequest) = coroutineScope {
        val inventory = async { checkInventory(request) }
        val payment   = async { reservePayment(request) }
        awaitAll(inventory, payment)
        // 여기서 요청 완료 → 훅 실행 → span 반영됨
    }
}
```

**Fire-and-forget (`launch`) — `@Async`와 동일한 제한**

코루틴이 요청보다 오래 살아있으면, 완료 시점에 이미 훅이 실행된 이후입니다. 컨텍스트 필드(traceId, issuer)는 직접 로깅에 사용할 수 있지만, 훅 기반 로깅과 span 기록은 요청 라이프사이클 밖에서 이루어집니다.

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    sendNotification()  // 응답이 이미 전송된 후에도 실행될 수 있음
}
```

### 요약

| 패턴 | `Operations.context` 접근 | Span 전파 | 훅 실행 |
|------|--------------------------|-----------|---------|
| Structured (`async`/`coroutineScope`) | ✓ | ✓ | ✓ |
| Fire-and-forget (`launch`) | ✓ | ✗ | ✗ |

---

## Spring Security 연동

Spring Security가 클래스패스에 있으면, 보안 컨텍스트에서 자동으로 발급자를 추출합니다.

```
issuer = authentication.name  # 인증된 사용자
issuer = "anonymous"          # 미인증 사용자
```

Spring Security는 **선택적** 의존성입니다. 없어도 라이브러리는 정상 동작합니다.

---

## Micrometer 연동

`MeterRegistry`가 클래스패스에 있고 `micrometer.enabled=true`이면, Micrometer 기반의 `MetricsRecorder` Bean이 자동 등록됩니다.

Micrometer가 없을 경우 no-op `MetricsRecorder`가 fallback으로 사용되어 애플리케이션이 정상적으로 시작됩니다.

Prometheus로 메트릭을 내보내려면 다음을 포함하세요:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

메트릭은 `/actuator/prometheus`에서 노출됩니다.

---

## 라이브러리 확장

커스텀 Bean을 등록하면 기본 프로바이더를 자유롭게 교체할 수 있습니다.

| 인터페이스 | 역할 |
|------------|------|
| `TraceIdProvider` | 커스텀 트레이스 ID 생성 |
| `SpanIdProvider` | 커스텀 스팬 ID 생성 |
| `CausationIdProvider` | 커스텀 인과관계 ID 생성 |
| `IssuerProvider` | 커스텀 발급자 추출 |
| `TelemetryPropagationProvider` | 커스텀 헤더 전파 표준 |
| `MetricsRecorder` | 커스텀 메트릭 백엔드 |
| `OperationHook` | 커스텀 라이프사이클 콜백 |

---

## 주의사항 및 제한

- **스레드 로컬 컨텍스트**: `ManagedContext`는 `ThreadLocal`에 저장됩니다. `@Async` 전파는 `ManagedContextTaskDecorator`를 통해 자동으로 처리됩니다. Kotlin 코루틴은 `ManagedContextElement`를 통한 명시적 전파가 필요합니다. Fire-and-forget 패턴(`launch`)에서는 설계상 훅과 span 기록이 요청 라이프사이클 밖에서 이루어집니다.
- **Spring AOP 자기 호출**: 동일 클래스 내부의 메서드 호출은 AOP Aspect가 인터셉트하지 않습니다.
- **스트리밍 응답**: `traceparent` 응답 헤더는 요청 처리 완료 후 설정됩니다. 스트리밍 또는 비동기 응답에서는 헤더가 전달되지 않을 수 있습니다.
- **`Operations.context` 범위**: 관리 범위 밖에서 `Operations.context`를 호출하면 명확한 메시지와 함께 `IllegalStateException`이 발생합니다.

---

## 로드맵

- [ ] Maven Central 배포
- [ ] WebFlux 지원
- [x] 비동기 컨텍스트 전파 (`@Async`는 `ManagedContextTaskDecorator`, 코루틴은 `ManagedContextElement`)
- [ ] OpenTelemetry SDK 연동
- [ ] `@ManagedRepository` 메트릭 계측 구현
- [ ] 고트래픽 환경을 위한 샘플링 지원

---

## 라이선스

이 프로젝트는 오픈소스입니다. 기여를 환영합니다.
