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

권장 패턴은 서비스의 주요 비즈니스 로직 핸들러에 `@ManagedOperation`을 붙이고, `Operations { }` 블록의 결과를 직접 반환하는 것입니다. Kotlin의 타입 추론으로 반환 타입이 자동으로 결정되며, 명시적으로 선언하는 것도 가능합니다.

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

    // 반환 타입이 OperationResult<OrderResponse>로 자동 추론됨
    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        val order = orderRepository.save(Order.from(request))
        OrderResponse.from(order)
    }

    // 명시적 반환 타입도 가능
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

### 2. Operations로 컨텍스트 접근

`Operations.context`는 관리 범위 내 어디서든 전역으로 접근 가능합니다 — `Operations { }` 블록 안에서만 사용할 수 있는 것이 아닙니다. 동일한 managed trigger(HTTP 요청, Kafka 메시지, gRPC 호출, 또는 향후 추가될 프로토콜) 내에서 실행되는 모든 코드는 동일한 `ManagedContext` 인스턴스를 공유합니다.

```kotlin
@Service
class AuditService {
    fun record() {
        val ctx = Operations.context  // 관리 범위 내 어디서든 접근 가능
        println(ctx.traceId)
        println(ctx.issuer)
    }
}
```

컨텍스트 상태는 누적되며 접근 시점의 라이프사이클을 반영합니다. 어노테이션(`@ManagedController`, `@ManagedService`, `@ManagedOperation`)이 주입하는 값은 각 레이어에 진입할 때 채워집니다. 어노테이션이 처리되기 전에 `Operations.context`에 접근하면 해당 필드는 아직 비어있습니다.

```kotlin
// 진입점에서    → traceId ✓  issuer ✓  service ✗  operation ✗
// 서비스에서    → traceId ✓  issuer ✓  service ✓  operation ✗
// @ManagedOperation 메서드에서 → 모든 필드 ✓
```

`Operations { }` 블록은 컨텍스트와 함께 결과값도 캡처합니다:

```kotlin
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
| `@ManagedRepository` | 클래스 | 리포지토리의 모든 메서드를 DB 수준 메트릭 span으로 계측 |
| `@ManagedOperation` | 메서드 | `operation`, `useCase` 값을 컨텍스트에 주입하고 루트 메트릭 span 생성 |
| `@ManagedMetric` | 메서드 | 임의 메서드를 이름이 있는 자식 메트릭 span으로 계측 |

> `@ManagedController`, `@ManagedService`, `@ManagedRepository`는 클래스명을 식별자로 사용합니다. `@ManagedMetric`은 기본적으로 `ClassName.methodName`을 span 이름으로 사용하며 `name` 속성으로 재정의할 수 있습니다 — 메트릭 태그 폭발을 방지하기 위해 값은 저 카디널리티로 유지하세요.

---

## 훅 시스템

훅을 통해 오퍼레이션 라이프사이클 이벤트(성공 또는 실패)에 반응할 수 있습니다.

### 훅 순서 (Hook Ordering)

훅은 Spring의 `@Order` 어노테이션으로 순서가 결정됩니다. 내장 `DefaultOperationLoggingHook`은 **Order 50**으로 등록되며, 이를 기준점으로 삼습니다.

```
Order < 50  →  전처리 훅          (컨텍스트 보강, 사전 처리)
Order 50    →  DefaultOperationLoggingHook  (보강된 컨텍스트를 로그로 출력)
Order 60    →  MetricsOperationHook   (전체 span 트리를 Micrometer에 기록)
Order > 60  →  커스텀 후처리 훅   (알림, 경보 등)
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

**설정 옵션:**

| 프로퍼티 | 기본값 | 설명 |
|----------|--------|------|
| `pretty` | `false` | 사람이 읽기 쉬운 포맷으로 출력 |
| `json` | `true` | JSON 포맷 출력 (프로덕션 권장) |
| `spans` | `false` | pretty 출력에 span 트리 포함 |
| `success-level` | `INFO` | 성공 시 로그 레벨 |
| `failure-level` | `ERROR` | 실패 시 로그 레벨 |

**Pretty 출력 예시 (`spans: true` 설정 시):**
```
┌───────────────────────────────────────────────────────────────────────────────────
│ ✅ Success
├─ Status      : SUCCESS
├─ TraceId     : 4bf92f3577b34da6a3ce929d0e0e4736
├─ CausationId : 00f067aa0ba902b7
├─ Issuer      : john.doe
├─ Protocol    : HTTP
├─ Type        : REST
├─ HTTP_URI    : /orders
├─ HTTP_METHOD : POST
├─ Entry Point : OrderController
├─ Service     : OrderService
├─ Operation   : CreateOrder
├─ UseCase     : PlaceOrder
├─ Message     :
├─ Response    :
├─ Performance : 56Ms
├─ Timestamp   : 2025-05-07T12:34:56.789Z
├─ Hooks       : MyEnrichmentHook=OK
├─ Spans      :
│    CreateOrder  [56ms]  SUCCESS
│         └─ validateInventory  [9ms]  SUCCESS
│         └─ OrderRepository.save  [18ms]  SUCCESS
│         └─ OrderRepository.findByCustomerId  [7ms]  SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

---

## Span 메트릭

어노테이션이 붙은 모든 메서드 경계는 **메트릭 span** — 결과, 태그, 중첩 구조를 가진 시간 측정 단위 — 으로 캡처됩니다. Span들은 `@ManagedOperation`을 루트로 하는 트리를 형성합니다.

### Span 계층 구조

```
@ManagedOperation  ──  루트 span (operation + useCase 태그)
    └── @ManagedMetric      ──  커스텀 자식 span (직접 계측할 임의 메서드)
    └── @ManagedRepository  ──  DB 자식 span (리포지토리 메서드 호출마다 하나)
```

### 예시

```kotlin
@ManagedService
@Service
class OrderService(private val repo: OrderRepository) {

    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        validateInventory(request)          // @ManagedMetric 자식 span
        repo.save(Order.from(request))      // @ManagedRepository 자식 span
    }

    @ManagedMetric(name = "validateInventory")
    private fun validateInventory(request: OrderRequest) { ... }
}

@ManagedRepository
@Repository
class OrderRepository {
    fun save(order: Order): Order { ... }   // DB span으로 자동 캡처
}
```

Span들은 요청 처리 중에 수집되어, 요청이 완료될 때 `MetricsOperationHook`(Order 60)이 Micrometer에 일괄 기록합니다. 각 span은 `omk.span.duration`(타이머) 또는 `omk.span.count`(카운터)로 기록되며 다음 태그가 붙습니다:

| 태그 | 출처 |
|------|------|
| `service` | `@ManagedService` 클래스명 |
| `operation` | `@ManagedOperation.operation` |
| `use_case` | `@ManagedOperation.useCase` |
| `repository` | 리포지토리 클래스명 (DB span) |
| `method` | 리포지토리 메서드명 (DB span) |
| `span` | span 이름 (`@ManagedMetric` span) |
| `status` | `success` 또는 `failure` |
| `error_type` | 예외 클래스명 (실패 시) |

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
      spans: false        # pretty 출력에 span 트리 포함 (기본값: false)
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

## 스레드 생명주기와 컨텍스트 공유

메인 요청 스레드와 async/코루틴 스레드의 관계를 이해하면 컨텍스트 동작을 명확하게 파악할 수 있습니다.

**컨텍스트가 공유되는 방식**

`ManagedContext`는 힙에 존재하는 단일 객체입니다. 메인 스레드와 async/코루틴 스레드는 각자의 `ThreadLocal` 슬롯을 통해 같은 인스턴스의 참조를 보유합니다. 어느 스레드에서 컨텍스트를 수정해도 동일한 객체가 변경됩니다.

```
힙
├── ManagedContext@1a2b3c  ←─────────────────────────┐
│                                                      │
ThreadLocal 슬롯                                       │
├── main thread 슬롯  → ManagedContext@1a2b3c ─────────┤
└── async thread 슬롯 → ManagedContext@1a2b3c ─────────┘
                              (동일한 인스턴스)
```

**메인 스레드의 생명주기**

메인 요청 스레드는 서블릿 필터가 관리하는 고정된 생명주기를 따릅니다.

```
요청 수신 → 컨텍스트 생성 → 비즈니스 로직 → 훅 실행 → clear() → 응답 반환
```

`clear()`는 메인 스레드의 `ThreadLocal` 슬롯에서 참조를 제거하며 요청 라이프사이클의 종료를 의미합니다.

**Async/코루틴 스레드의 생명주기는 보장되지 않음**

Async/코루틴 스레드는 독립적으로 실행됩니다. 메인 스레드가 `clear()`를 호출하기 전에 끝날 수도 있고, 그 이후에 끝날 수도 있습니다. 이로 인해 두 가지 결과가 발생합니다:

- **`clear()` 이전에 완료**: 공유 인스턴스에 대한 write가 훅 실행 시점에 반영됨 → 로그 및 메트릭에 기록됨 ✓
- **`clear()` 이후에 완료**: 메인 스레드의 슬롯은 이미 비워지고 훅도 이미 실행됨 → 공유 인스턴스에 대한 write는 조용히 무시됨 (에러 없음) ✗

**Structured concurrency가 안정적으로 동작하는 이유**

`coroutineScope`, `awaitAll`, `runBlocking` 같은 structured concurrency 연산자는 모든 자식 코루틴이 완료될 때까지 부모를 대기시킵니다. 이를 통해 메인 스레드가 훅 실행과 `clear()` 단계로 진행하기 전에 모든 write가 완료됨을 보장합니다.

**Fire-and-forget이 컨텍스트 write에 신뢰할 수 없는 이유**

`launch`와 `@Async` (fire-and-forget)는 즉시 반환하여 async 작업이 아직 실행 중인 상태에서 메인 스레드가 훅 실행과 `clear()`로 진행합니다. `clear()` 이후에 발생하는 write는 조용히 버려집니다 — 힙의 객체는 여전히 존재하지만(async 스레드가 참조를 보유), 아무도 그 변경을 처리하지 않습니다.

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
- [x] Span 수준 메트릭 계측 (`@ManagedOperation`, `@ManagedMetric`, `@ManagedRepository` DB span)
- [x] Micrometer 기반 `MetricsOperationHook` 및 전체 span 트리 기록
- [ ] OpenTelemetry SDK 연동
- [ ] 고트래픽 환경을 위한 샘플링 지원

---

## 라이선스

이 프로젝트는 오픈소스입니다. 기여를 환영합니다.
