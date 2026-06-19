# OMK — Spring WebMVC (Servlet 스택)

**한국어 | [English](Servlet.md)**

→ [README로 돌아가기](README.ko.md)

---

## 목차

- [빠른 시작](#빠른-시작)
- [어노테이션](#어노테이션)
- [훅 시스템](#훅-시스템)
- [Span 메트릭](#span-메트릭)
- [설정 프로퍼티](#설정-프로퍼티)
- [`@Async` 컨텍스트 전파](#async-컨텍스트-전파)
- [Kotlin 코루틴 컨텍스트 전파](#kotlin-코루틴-컨텍스트-전파)
- [이벤트 드리븐 컨텍스트 전파](#이벤트-드리븐-컨텍스트-전파-managedeventhandler)
- [Spring Security 연동](#spring-security-연동)
- [분산 추적](#분산-추적)
- [관측 파이프라인](#관측-파이프라인)
- [Logback 연동](#logback-연동)
- [라이브러리 확장](#라이브러리-확장)
- [주의사항 및 제한](#주의사항-및-제한)

---

## 빠른 시작

### 1. 컴포넌트에 어노테이션 붙이기

권장 패턴은 서비스의 주요 비즈니스 로직 핸들러에 `@ManagedOperation`을 붙이고, `Operations { }` 블록의 결과를 직접 반환하는 것입니다.

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

    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        val order = orderRepository.save(Order.from(request))
        OrderResponse.from(order)
    }

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

`Operations.context`는 관리 범위 내 어디서든 전역으로 접근 가능합니다.

```kotlin
@Service
class AuditService {
    fun record() {
        val ctx = Operations.context
        println(ctx.traceId)
        println(ctx.issuer)
    }
}
```

컨텍스트 상태는 누적되며 접근 시점의 라이프사이클을 반영합니다:

```kotlin
// 진입점에서         → traceId ✓  issuer ✓  service ✗  operation ✗
// 서비스에서         → traceId ✓  issuer ✓  service ✓  operation ✗
// @ManagedOperation  → 모든 필드 ✓
```

`Operations { }` 블록은 컨텍스트와 함께 결과값도 캡처합니다:

```kotlin
val result = Operations { // this: ManagedContext
    println(traceId)
    "OK"
}
println(result.context.traceId)
println(result.data) // "OK"
```

---

## 어노테이션

| 어노테이션                  | 대상  | 역할                                                   |
|------------------------|-----|------------------------------------------------------|
| `@ManagedController`   | 클래스 | 핸들러 메서드마다 ENTRY 레이어 루트 span 생성; entrypoint 주입        |
| `@ManagedService`      | 클래스 | 서비스 클래스명을 컨텍스트의 service에 주입                          |
| `@ManagedRepository`   | 클래스 | 리포지토리의 모든 메서드를 DB 레이어 자식 span으로 계측                   |
| `@ManagedOperation`    | 메서드 | `operation`, `useCase` 값 주입; APPLICATION 레이어 span 생성 |
| `@ManagedMetric`       | 메서드 | 임의 메서드를 이름이 있는 APPLICATION 레이어 자식 span으로 계측          |
| `@ManagedEventHandler` | 메서드 | 메시징 핸들러에 ENTRY 레이어 span 생성; 이벤트 인자에서 트레이스 컨텍스트 자동 추출 |

---

## 훅 시스템

### 훅 순서

```
Order < 50  →  전처리 훅 (컨텍스트 보강, 사전 처리)
Order 50    →  DefaultOperationLoggingHook
Order 60    →  MetricsOperationHook
Order > 60  →  커스텀 후처리 훅
```

### Outcome 분류 (onSuccess vs onFailure)

`filterChain.doFilter()`가 예외 없이 반환되면, `ManagedContextPersistenceFilter`는 `context.injectStatusCode(response.status)`를 호출하여 최종 HTTP 상태 코드로부터 `context.outcome`([OperationOutcome](API.ko.md#operationoutcome) 참고)을 도출합니다.

- `outcome == SERVER_ERROR`(5xx)이면 예외가 발생하지 않았더라도 `onFailure`가 호출됩니다 (예: `response.sendError(500)` 또는 `response.status = 500`을 직접 설정한 경우).
- 그 외의 모든 outcome(`UNAUTHENTICATED`(401), `FORBIDDEN`(403) 포함)은 여전히 `onSuccess`가 호출됩니다 — 요청이 정상적으로 처리되어 의도한 응답이 반환되었기 때문입니다 (예: Spring Security의 `AuthenticationEntryPoint`).

`DefaultOperationLoggingHook`은 `onSuccess` 내부에서 `context.outcome`을 확인하여, `SUCCESS`가 아닌 outcome은 `logging.success-level` 대신 `logging.client-error-level`(기본값 `WARN`)로 로깅합니다.

### 예외 캡처 (`@ExceptionHandler` / `@ControllerAdvice`)

`@RestControllerAdvice`가 예외를 정상 응답으로 변환하는 경우(도메인/검증 예외에서 흔한 패턴), 이 변환은 `ManagedContextPersistenceFilter`로 제어가 돌아오기 **전에** `DispatcherServlet` 내부에서 일어납니다. 필터가 실행될 때는 원본 예외 객체가 이미 사라지고, 최종 HTTP 상태 코드만 보입니다:

- `SERVER_ERROR`(5xx)의 경우, 필터는 실제 예외가 전달되지 않았으므로 `onFailure`에 넘길 가짜 `RuntimeException("HTTP 500")`을 만들어내야 했습니다.
- 그 외 outcome의 경우, `onSuccess`가 예외 정보 없이 호출되었습니다 — `DomainValidationException`이 던져져도 로그상으로는 정상 요청과 구분이 안 됐습니다.

`ExceptionCapturingResolver`는 `Ordered.HIGHEST_PRECEDENCE`로 등록되는 `HandlerExceptionResolver`입니다. `@ExceptionHandler` 메서드보다 먼저 실행되어 예외를 `ManagedContext.capturedException`에 기록한 뒤 `null`을 반환해서, 실제 예외 처리는 원래 작성한 `@ExceptionHandler`가 그대로 수행합니다. 이후 필터와 `DefaultOperationLoggingHook`은 가짜 예외를 만드는 대신 `context.capturedException`을 읽습니다 — 5xx는 `onFailure`에 진짜 예외가 전달되고, 4xx/401/403도 `onSuccess`의 로그 출력에 진짜 예외의 타입/메시지/스택트레이스가 포함됩니다. `onSuccess` vs `onFailure` 라우팅 자체는 그대로입니다(위와 동일하게 상태 코드 기준) — 어떤 예외가 보이는지만 달라집니다.

기본적으로 활성화되어 있으며, `operation-manager.webmvc.exception-capture.enabled: false`로 끌 수 있습니다.

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

| 프로퍼티            | 기본값     | 설명                         |
|-----------------|---------|----------------------------|
| `pretty`        | `false` | 사람이 읽기 쉬운 포맷               |
| `json`          | `true`  | JSON 포맷 (프로덕션 권장)          |
| `spans`         | `false` | pretty 출력에 span 트리 포함      |
| `response`      | `true`  | Operations 블록의 반환값을 로그에 포함 |
| `success-level` | `INFO`  | 성공 시 로그 레벨                 |
| `failure-level` | `ERROR` | 실패 시 로그 레벨                 |
| `client-error-level` | `WARN` | `outcome`이 `SUCCESS`가 아닌 `onSuccess` 호출(`UNAUTHENTICATED`, `FORBIDDEN`, `CLIENT_ERROR` 등)의 로그 레벨 |

**Pretty 출력 예시 (`spans: true` 설정 시):**
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
├─ Performance : 56Ms
├─ Spans      :
│    [ENT] 12:34:56.733  [nio-8080-exec-1]  OrderController.create   [56ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [nio-8080-exec-1]  CreateOrder      [48ms]   SUCCESS
│                   └─ [DB ] 12:34:56.751  [nio-8080-exec-1]  OrderRepository.save  [18ms]  SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

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
  webmvc:
    context-filter:
      enabled: true
      exclude-options: true   # true이면 OPTIONS(CORS preflight) 요청은 컨텍스트 생성/로깅 없이 통과 (기본값: true)

    context-aspect:
      enabled: true

    exception-capture:
      enabled: true       # ExceptionCapturingResolver를 등록해서 onFailure/로그가 @ExceptionHandler가 잡은 진짜 예외를 보게 함

    micrometer:
      enabled: true

    otel:
      enabled: true       # Tracer Bean 필요

    logging:
      enabled: true
      pretty: false
      json: true
      spans: false
      success-level: INFO
      failure-level: ERROR

    async-propagation:
      enabled: true
      hook-enabled: false

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        generate-when-missing: true
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## `@Async` 컨텍스트 전파

`@EnableAsync` 활성화 시 `ManagedContextTaskDecorator`가 자동 등록됩니다. `@Async` 메서드 호출 시 컨텍스트는 **포크**됩니다 — 각 async 스레드는 독립적인 복사본을 받습니다.

| 필드                                 | 동작          |
|------------------------------------|-------------|
| `traceId`, `causationId`, `issuer` | 상속          |
| `executionScope`                   | `ASYNC`로 설정 |
| Span 트리, 타이밍, 훅 레코드                | 독립          |

### Java 21 Virtual Thread 지원

```yaml
spring:
  threads:
    virtual:
      enabled: true
# OMK 별도 설정 불필요
```

---

## Kotlin 코루틴 컨텍스트 전파

`ManagedContextElement`가 suspension point 사이에서 컨텍스트를 전파합니다:

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    // 포크된 컨텍스트: traceId 상속, 독립적인 span 트리
    Operations.context.traceId  // 모든 suspension point 이후 접근 가능
}
```

---

## 이벤트 드리븐 컨텍스트 전파 (`@ManagedEventHandler`)

### 자동 컨텍스트 추출 우선순위

| 우선순위 | 소스                                                          |
|------|-------------------------------------------------------------|
| 1순위  | `@ManagedEvent*` 필드 어노테이션                                   |
| 2순위  | Kafka `ConsumerRecord` 헤더 (W3C traceparent → X-Trace-Id 폴백) |
| 3순위  | Spring `Message<*>` 헤더                                      |
| 4순위  | 덕 타이핑 (리플렉션으로 `traceId`, `causationId` 등 탐색)                |
| 5순위  | `generate-when-missing`                                     |

```kotlin
@Component
class OrderEventHandler {

    @ManagedEventHandler
    @KafkaListener(topics = ["order.created"])
    fun handle(event: OrderCreatedEvent) {
        // Operations.context 사용 가능 — executionScope = EVENT
    }
}
```

---

## Spring Security 연동

Spring Security가 클래스패스에 있으면 보안 컨텍스트에서 발급자를 자동 추출합니다:

```
issuer = authentication.name   # 인증된 사용자
issuer = "anonymous"           # 미인증 사용자
```

Spring Security는 **선택적** 의존성입니다.

---

## 분산 추적

기본적으로 W3C Trace Context 표준 사용:

```
traceparent: 00-{traceId 32hex}-{spanId 16hex}-01
```

`mode: CUSTOM`으로 커스텀 헤더 사용 가능합니다.

---

## 관측 파이프라인

```
OMK span 트리
    ├── MetricsOperationHook  →  Micrometer  →  Prometheus / Grafana Mimir
    └── OtelOperationHook     →  OpenTelemetry  →  Tempo / Jaeger / Zipkin
```

### Micrometer

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")
```

### OpenTelemetry

```kotlin
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

```yaml
management:
  otlp:
    tracing:
      endpoint: http://tempo:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

---

## Logback 연동

| 로거                        | 내용               |
|---------------------------|------------------|
| `OperationManager.Pretty` | 사람이 읽기 좋은 박스 포맷  |
| `OperationManager.JSON`   | 구조화된 JSON (프로덕션) |

```xml
<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>
<logger name="OperationManager.JSON" level="OFF" additivity="false"/>
```

---

## 라이브러리 확장

| 인터페이스                          | 역할             |
|--------------------------------|----------------|
| `TraceIdProvider`              | 커스텀 트레이스 ID 생성 |
| `SpanIdProvider`               | 커스텀 스팬 ID 생성   |
| `CausationIdProvider`          | 커스텀 인과관계 ID 생성 |
| `IssuerProvider`               | 커스텀 발급자 추출     |
| `TelemetryPropagationProvider` | 커스텀 헤더 전파 표준   |
| `MetricsRecorder`              | 커스텀 메트릭 백엔드    |
| `OperationHook`                | 커스텀 라이프사이클 콜백  |

---

## 주의사항 및 제한

- **Spring AOP 자기 호출**: 동일 클래스 내부의 메서드 호출은 AOP Aspect가 인터셉트하지 않습니다.
- **`Operations.context` 범위**: 관리 범위 밖에서 호출하면 `IllegalStateException`이 발생합니다.
- **스트리밍 응답**: `traceparent` 응답 헤더는 스트리밍 또는 비동기 응답에서 전달되지 않을 수 있습니다.
- **스레드 로컬 컨텍스트**: `ManagedContext`는 `ThreadLocal`에 저장됩니다. Kotlin 코루틴은 `ManagedContextElement`를 통한 명시적 전파가 필요합니다.

---