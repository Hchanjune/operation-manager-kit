# OMK — Spring WebFlux (Reactive 스택)

**한국어 | [English](Reactive.md)**

→ [README로 돌아가기](README.ko.md)

---

## 사전 준비

### 의존성

```kotlin
dependencies {
    implementation("com.github.Hchanjune.operation-manager-kit:core:x.x.x")
    implementation("com.github.Hchanjune.operation-manager-kit:spring-webflux:x.x.x")

    implementation("org.aspectj:aspectjweaver")
    implementation("io.micrometer:micrometer-core")

    // kotlin-reflect 버전이 kotlin-stdlib 버전과 일치해야 함
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}
```

> **중요**: `kotlin-reflect`는 반드시 `kotlin-stdlib`와 동일한 버전이어야 합니다. 버전이 다른 경우(예: reflect 1.6 + stdlib 2.x) Spring WebFlux가 `suspend fun` 메서드를 정상적으로 감지하지 못해 트레이스 컨텍스트 전파가 동작하지 않습니다.

### 설정

```yaml
operation-manager:
  webflux:
    context-aspect:
      enabled: true
    logging:
      pretty: true
      spans: true
    telemetry:
      propagation:
        mode: W3C_STANDARD
        generate-when-missing: true
```

---

## 빠른 시작

### 1. 컴포넌트에 어노테이션 붙이기

`spring-webflux`는 `suspend fun`과 `Mono`/`Flow` 반환 메서드를 모두 지원합니다.

```kotlin
import io.github.hchanjune.omk.core.annotations.ManagedController
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.core.annotations.ManagedOperation

@RestController
@ManagedController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {

    // suspend fun — WebFlux + Coroutines 환경에서 권장
    @GetMapping("/{id}")
    suspend fun getById(@PathVariable id: String): OrderResponse =
        orderService.findById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@RequestBody request: CreateOrderRequest): OrderResponse =
        orderService.create(request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(@PathVariable id: String) =
        orderService.delete(id)
}

@Service
@ManagedService
class OrderService(private val orderRepository: OrderRepository) {

    @ManagedOperation(operation = "FindOrder", useCase = "OrderManagement")
    suspend fun findById(id: String): OrderResponse {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        return OrderResponse.from(order)
    }

    @ManagedOperation(operation = "CreateOrder", useCase = "OrderManagement")
    suspend fun create(request: CreateOrderRequest): OrderResponse {
        val saved = orderRepository.save(Order.from(request))
        return OrderResponse.from(saved)
    }

    @ManagedOperation(operation = "DeleteOrder", useCase = "OrderManagement")
    suspend fun delete(id: String) {
        val order = orderRepository.findById(id) ?: throw OrderNotFoundException(id)
        orderRepository.delete(order)
    }
}
```

### 2. ReactiveOperations로 컨텍스트 접근

WebFlux + Coroutines 애플리케이션에서는 `ReactiveOperations`를 사용해 관리 컨텍스트에 접근합니다:

```kotlin
@Service
@ManagedService
class AuditService {

    @ManagedOperation(operation = "Audit", useCase = "Compliance")
    suspend fun record(): AuditResult {
        val result = ReactiveOperations {  // this: ManagedContext
            println(traceId)
            println(issuer)
            println(operation)
            AuditResult(traceId = traceId, issuer = issuer)
        }
        return result.data
    }
}
```

`ReactiveOperations { }`는 WebFilter가 전파한 Reactor 컨텍스트에서 `ManagedContext`를 읽어옵니다 — 관리 HTTP 요청 내의 코루틴 호출 체인 어디서든 접근 가능합니다.

컨텍스트 상태는 누적됩니다:

```kotlin
// 컨트롤러 진입 시  → traceId ✓  issuer ✓  service ✗  operation ✗
// 서비스에서        → traceId ✓  issuer ✓  service ✓  operation ✗
// @ManagedOperation → 모든 필드 ✓
```

---

## 어노테이션

| 어노테이션 | 대상 | 역할 |
|------------|------|------|
| `@ManagedController` | 클래스 | 핸들러 메서드마다 ENTRY 레이어 루트 span 생성; entrypoint 주입 |
| `@ManagedService` | 클래스 | 서비스 클래스명을 컨텍스트의 service에 주입 |
| `@ManagedRepository` | 클래스 | 리포지토리의 모든 메서드를 DB 레이어 자식 span으로 계측 |
| `@ManagedOperation` | 메서드 | `operation`, `useCase` 값 주입; APPLICATION 레이어 span 생성 |
| `@ManagedMetric` | 메서드 | 임의 메서드를 이름이 있는 APPLICATION 레이어 자식 span으로 계측 |
| `@ManagedEventHandler` | 메서드 | 메시징 핸들러에 ENTRY 레이어 span 생성; 이벤트 인자에서 트레이스 컨텍스트 자동 추출 |

> `@ManagedRepository`는 클래스 레벨 어노테이션을 대상으로 합니다. Spring Data reactive 리포지토리는 인터페이스이므로, `CoroutineCrudRepository` 인터페이스에 `@ManagedRepository`를 직접 적용해도 효과가 없습니다. 대신 리포지토리를 호출하는 서비스 메서드에 `@ManagedMetric`을 사용하세요.

---

## 훅 시스템

### 훅 순서

```
Order < 50  →  전처리 훅 (컨텍스트 보강, 사전 처리)
Order 50    →  DefaultOperationLoggingHook
Order 60    →  MetricsOperationHook
Order > 60  →  커스텀 후처리 훅
```

### 커스텀 훅 예시

```kotlin
@Component
@Order(30)
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

| 프로퍼티 | 기본값 | 설명 |
|----------|--------|------|
| `pretty` | `false` | 사람이 읽기 쉬운 포맷 |
| `json` | `true` | JSON 포맷 (프로덕션 권장) |
| `spans` | `false` | pretty 출력에 span 트리 포함 |
| `response` | `true` | Operations 블록의 반환값을 로그에 포함 |
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
├─ HTTP_URI    : /api/orders
├─ HTTP_METHOD : POST
├─ Entry Point : OrderController
├─ Service     : OrderService
├─ Operation   : CreateOrder
├─ UseCase     : OrderManagement
├─ Performance : 42Ms
├─ Spans      :
│    [ENT] 12:34:56.733  [reactor-http-nio-2]  OrderController.create   [42ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [reactor-http-nio-2]  CreateOrder      [35ms]   SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

> span 트리에서 같은 스레드(`reactor-http-nio-*`)가 표시되는 것은 WebFlux가 논블로킹 이벤트 루프를 사용하기 때문이며, 정상적인 동작입니다. 블로킹 작업에 `Dispatchers.IO`를 사용할 때만 스레드 전환이 발생합니다.

---

## Span 메트릭

### Span 계층 구조

```
@ManagedController    ──  [ENT] 루트 span
@ManagedEventHandler  ──  [ENT] 루트 span  (executionScope = EVENT)
    └── @ManagedOperation  ──  [APP] 자식 span
            └── @ManagedMetric      ──  [APP] 자식 span
            └── @ManagedRepository  ──  [DB]  자식 span
```

요청이 완료될 때 `MetricsOperationHook`(Order 60)이 Micrometer에 `omk.span.duration`으로 기록합니다.

---

## 설정 프로퍼티

```yaml
operation-manager:
  webflux:
    context-filter:
      enabled: true       # WebFilter 활성화 (기본값: true)

    context-aspect:
      enabled: true       # AOP Aspect 활성화 (기본값: true)

    micrometer:
      enabled: true       # Micrometer 메트릭 기록 활성화 (기본값: true)

    logging:
      enabled: true
      pretty: false       # 사람이 읽기 쉬운 포맷
      json: true          # JSON 포맷 (프로덕션 권장)
      spans: false        # pretty 출력에 span 트리 포함 (기본값: false)
      success-level: INFO
      failure-level: ERROR

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        generate-when-missing: true
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## 컨텍스트 전파 동작 방식

`ManagedContextWebFilter`가 요청을 받아 `ManagedContext`를 생성하고 `.contextWrite(...)`를 통해 Reactor 컨텍스트에 저장합니다. 이후 컨트롤러와 서비스의 모든 suspend 함수는 이 Reactor 컨텍스트를 자동으로 상속받습니다.

```
요청 → ManagedContextWebFilter (contextWrite)
           ↓
       Coroutines Utils (mono { ... })
           ↓
       Controller (suspend fun) → AOP Aspect가 ReactorContext에서 컨텍스트 읽음
           ↓
       Service (suspend fun)    → AOP Aspect가 ReactorContext에서 컨텍스트 읽음
           ↓
       응답 → beforeCommit → hook.onSuccess/onFailure
```

AOP Aspect는 continuation의 `ReactorContext`에서 `ManagedContext`를 읽습니다 — ThreadLocal을 사용하지 않기 때문에 WebFlux에서 suspension point와 스레드 전환에 걸쳐 컨텍스트가 올바르게 전파됩니다.

---

## 이벤트 드리븐 컨텍스트 전파 (`@ManagedEventHandler`)

`@ManagedEventHandler`는 `Mono` 반환과 `suspend fun` 핸들러를 모두 지원합니다.

### 자동 컨텍스트 추출 우선순위

| 우선순위 | 소스 |
|----------|------|
| 1순위 | `@ManagedEvent*` 필드 어노테이션 |
| 2순위 | Kafka `ConsumerRecord` 헤더 (W3C traceparent → X-Trace-Id 폴백) |
| 3순위 | Spring `Message<*>` 헤더 |
| 4순위 | 덕 타이핑 (리플렉션으로 `traceId`, `causationId` 등 탐색) |
| 5순위 | `generate-when-missing` |

```kotlin
@Component
class OrderEventHandler {

    @ManagedEventHandler
    suspend fun handle(event: OrderCreatedEvent) {
        // ReactiveOperations { } 사용 가능 — executionScope = EVENT
    }
}
```

---

## Spring Security 연동

Spring Security(`spring-boot-starter-security` WebFlux 버전)가 클래스패스에 있으면 `ReactiveSecurityContextHolder`에서 발급자를 자동 추출합니다:

```
issuer = authentication.name   # 인증된 사용자
issuer = "anonymous"           # 미인증 사용자
```

Spring Security가 없는 경우 기본값은 `"anonymous"`입니다.

---

## 분산 추적

기본적으로 W3C Trace Context 표준 사용:

```
traceparent: 00-{traceId 32hex}-{spanId 16hex}-01
```

커스텀 헤더:

```yaml
operation-manager:
  webflux:
    telemetry:
      propagation:
        mode: CUSTOM
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## 관측 파이프라인

```
OMK span 트리
    ├── MetricsOperationHook  →  Micrometer  →  Prometheus / Grafana Mimir
    └── OtelOperationHook     →  OpenTelemetry  →  Tempo / Jaeger / Zipkin
```

### Micrometer

```kotlin
implementation("io.micrometer:micrometer-core")
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

### OpenTelemetry

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

---

## Logback 연동

| 로거 | 내용 |
|------|------|
| `OperationManager.Pretty` | 사람이 읽기 좋은 박스 포맷 |
| `OperationManager.JSON` | 구조화된 JSON (프로덕션) |

```xml
<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>
<logger name="OperationManager.JSON" level="OFF" additivity="false"/>
```

---

## 라이브러리 확장

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

- **`kotlin-reflect` 버전**: `kotlin-stdlib`와 반드시 동일한 버전이어야 합니다. 버전 불일치 시 Spring WebFlux의 suspend fun 감지가 실패해 컨텍스트 전파가 동작하지 않습니다.
- **Spring AOP 자기 호출**: 동일 클래스 내부의 메서드 호출은 AOP Aspect가 인터셉트하지 않습니다.
- **인터페이스에 `@ManagedRepository`**: Spring Data reactive 리포지토리는 인터페이스이므로 직접 적용이 불가합니다. 서비스 메서드에 `@ManagedMetric`을 사용하세요.
- **이벤트 루프 스레드**: 논블로킹 애플리케이션에서는 span이 동일한 `reactor-http-nio-*` 스레드를 보여주는 것이 정상입니다. 블로킹 작업에만 `Dispatchers.IO`가 필요합니다.
- **`ReactiveOperations` 범위**: 관리 HTTP 요청 범위 밖에서 호출하면 `IllegalStateException`이 발생합니다.
