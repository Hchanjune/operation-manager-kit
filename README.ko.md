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
| `@ManagedController` | 클래스 | 핸들러 메서드마다 ENTRY 레이어 루트 span 생성; entrypoint를 컨텍스트에 주입 |
| `@ManagedService` | 클래스 | 서비스 클래스명을 컨텍스트의 service에 주입 |
| `@ManagedRepository` | 클래스 | 리포지토리의 모든 메서드를 DB 레이어 자식 span으로 계측 |
| `@ManagedOperation` | 메서드 | `operation`, `useCase` 값을 컨텍스트에 주입; APPLICATION 레이어 span 생성 |
| `@ManagedMetric` | 메서드 | 임의 메서드를 이름이 있는 APPLICATION 레이어 자식 span으로 계측 |
| `@ManagedEventHandler` | 메서드 | 메시징 핸들러에 ENTRY 레이어 span 생성; 이벤트 인자에서 트레이스 컨텍스트 자동 추출; `executionScope`를 `EVENT`로 설정 |

> `@ManagedController`는 핸들러 메서드 호출마다 하나의 span을 생성하며 HTTP 요청의 span 트리 루트가 됩니다. `@ManagedEventHandler`는 Kafka, RabbitMQ 등 메시징 기반 진입점에서 동일한 역할을 합니다. `@ManagedMetric`은 기본적으로 `ClassName.methodName`을 span 이름으로 사용하며 `name` 속성으로 재정의할 수 있습니다 — 메트릭 태그 폭발을 방지하기 위해 저 카디널리티 값을 유지하세요.

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
├─ Timestamp   : 2025-05-07T12:34:56.733Z
├─ Hooks       : MyEnrichmentHook=OK
├─ Spans      :
│    [ENT] 12:34:56.733  [nio-8080-exec-1]  OrderController.create        [56ms]   SUCCESS
│         └─ [APP] 12:34:56.741  [nio-8080-exec-1]  CreateOrder           [48ms]   SUCCESS
│                   └─ [APP] 12:34:56.742  [nio-8080-exec-1]  validateInventory  [9ms]   SUCCESS
│                   └─ [DB ] 12:34:56.751  [nio-8080-exec-1]  OrderRepository.save  [18ms]  SUCCESS
│                   └─ [DB ] 12:34:56.769  [nio-8080-exec-1]  OrderRepository.findByCustomerId  [7ms]  SUCCESS
└───────────────────────────────────────────────────────────────────────────────────
```

---

## Span 메트릭

어노테이션이 붙은 모든 메서드 경계는 **메트릭 span** — 결과, 태그, 중첩 구조를 가진 시간 측정 단위 — 으로 캡처됩니다. Span들은 entry point를 루트로 하는 트리를 형성합니다.

### Span 계층 구조

```
@ManagedController    ──  [ENT] 루트 span  (HTTP 진입점, 핸들러 메서드마다)
@ManagedEventHandler  ──  [ENT] 루트 span  (메시징 진입점, executionScope = EVENT)
    └── @ManagedOperation  ──  [APP] 자식 span  (operation + useCase 태그)
            └── @ManagedMetric      ──  [APP] 자식 span  (임의 메서드)
            └── @ManagedRepository  ──  [DB]  자식 span  (리포지토리 메서드마다)
```

`@ManagedController`와 `@ManagedEventHandler` 모두 없는 경우(직접 서비스 호출 등) `@ManagedOperation`이 루트 span이 됩니다.

### 예시

```kotlin
@ManagedController
@RestController
class OrderController(private val orderService: OrderService) {

    @PostMapping("/orders")
    fun create(@RequestBody request: OrderRequest): ResponseEntity<OrderResponse> {
        val result = orderService.create(request)   // [ENT] span이 하위 전체를 감쌈
        return ResponseEntity.ok(result.data)
    }
}

@ManagedService
@Service
class OrderService(private val repo: OrderRepository) {

    @ManagedOperation(operation = "CreateOrder", useCase = "PlaceOrder")
    fun create(request: OrderRequest) = Operations {
        validateInventory(request)          // @ManagedMetric [APP] span
        repo.save(Order.from(request))      // @ManagedRepository [DB] span
    }

    @ManagedMetric(name = "validateInventory")
    private fun validateInventory(request: OrderRequest) { ... }
}

@ManagedRepository
@Repository
class OrderRepository {
    fun save(order: Order): Order { ... }
}
```

Span들은 요청 처리 중에 수집되어, 요청이 완료될 때 `MetricsOperationHook`(Order 60)이 Micrometer에 일괄 기록합니다. 각 span은 `omk.span.duration`(타이머) 또는 `omk.span.count`(카운터)로 기록되며 다음 태그가 붙습니다:

| 태그 | 출처 | 레이어 |
|------|------|--------|
| `entrypoint` | 컨트롤러 클래스명 | ENT |
| `method` | 컨트롤러 메서드명 | ENT |
| `service` | `@ManagedService` 클래스명 | APP |
| `operation` | `@ManagedOperation.operation` | APP |
| `use_case` | `@ManagedOperation.useCase` | APP |
| `span` | span 이름 | APP (`@ManagedMetric`) |
| `repository` | 리포지토리 클래스명 | DB |
| `method` | 리포지토리 메서드명 | DB |
| `status` | `success` 또는 `failure` | 전체 |
| `error_type` | 예외 클래스명 (실패 시) | 전체 |

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

    otel:
      enabled: true       # OpenTelemetry 트레이스 export 활성화 (기본값: true, Tracer Bean 필요)

    logging:
      enabled: true
      pretty: false       # Pretty-print 포맷 (사람이 읽기 쉬운 형태)
      json: true          # JSON 포맷 (프로덕션 권장)
      spans: false        # pretty 출력에 span 트리 포함 (기본값: false)
      success-level: INFO
      failure-level: ERROR

    async-propagation:
      enabled: true       # @Async 컨텍스트 전파 활성화 (기본값: true)
      hook-enabled: false # 비동기 태스크/코루틴 완료 시 훅 실행 (기본값: false)

    telemetry:
      propagation:
        mode: W3C_STANDARD  # W3C_STANDARD | CUSTOM
        generate-when-missing: true  # 인입 헤더에 traceId/causationId가 없을 때 자동 생성 (기본값: true)
        custom-headers:
          trace-id: X-Trace-Id
          causation-id: X-Causation-Id
```

---

## `@Async` 컨텍스트 전파

`@EnableAsync`가 활성화된 경우, `ManagedContextTaskDecorator`가 자동으로 등록되어 기본 `ThreadPoolTaskExecutor`에 적용됩니다. `@Async` 메서드가 호출될 때, 컨텍스트는 **포크(fork)** 됩니다 — async 스레드는 동일한 객체의 참조가 아닌 독립적인 복사본을 받습니다.

### 전파되는 것 (포크됨)

| 필드 | 동작 |
|------|------|
| `traceId`, `causationId`, `issuer` | 상속 — 스레드 간 로그 상관관계를 위해 |
| `protocol`, `type`, `uri`, `method`, `entrypoint`, `service`, `operation`, `useCase` | 상속 — 일관된 태깅을 위해 |
| `executionScope` | 포크된 컨텍스트에서 `ASYNC`로 설정 |
| Span 트리, 타이밍, 훅 레코드 | **독립** — 부모와 공유되지 않음 |

### Async span 트리

async 스레드가 시작되면 `async.execution` 루트 span이 자동으로 생성됩니다. async 메서드 내의 `@ManagedOperation`, `@ManagedMetric`, `@ManagedRepository` 호출은 이 루트의 자식 span이 됩니다 — 메인 요청과 완전히 독립적인 span 트리를 형성합니다.

```
메인 스레드 span 트리:          Async 스레드 span 트리:
[ENT] OrderController           [ENT] async.execution
  └─ [APP] CreateOrder            └─ [APP] SendNotification
       └─ [DB] save                    └─ [DB] logRepository.insert
```

### 훅 실행

기본적으로 훅은 async 컨텍스트에서 실행되지 **않습니다**. 설정으로 전역 기본값을 활성화하거나, 비즈니스 로직에서 요청 단위로 제어할 수 있습니다.

**전역 기본값:**

```yaml
operation-manager:
  webmvc:
    async-propagation:
      enabled: true
      hook-enabled: true  # async 태스크 완료 시 훅 실행 (기본값: false)
```

**요청 단위 제어:**

컨텍스트가 포크될 때 `isAsyncHookEnabled` 플래그가 상속됩니다. async 호출 전에 이 값을 토글하여 어떤 태스크에서 훅을 실행할지 제어합니다:

```kotlin
// 이 요청의 async 태스크에서 훅 활성화
Operations.context.enableAsyncHook()
asyncService.doTrackedWork()  // 완료 시 훅 실행

// fire-and-forget 태스크는 비활성화
Operations.context.disableAsyncHook()
asyncService.fireAndForget()  // 훅 없음
```

> 훅은 태스크가 완료될 때 async 스레드에서 실행되며, 메인 스레드와 독립적으로 동작합니다. Async 메트릭은 별도로 기록되며 메인 요청의 span 트리에는 나타나지 않습니다.

컨텍스트 전파를 완전히 비활성화하려면:

```yaml
operation-manager:
  webmvc:
    async-propagation:
      enabled: false
```

> 커스텀 `AsyncConfigurer` 또는 커스텀 `ThreadPoolTaskExecutor`를 사용하는 경우, `ManagedContextTaskDecorator`를 직접 주입해서 수동으로 적용하세요.

---

## Kotlin 코루틴 컨텍스트 전파

`kotlinx-coroutines-core`가 클래스패스에 있으면, `ManagedContextElement`가 suspension point와 스레드 전환 사이에서 관리 컨텍스트를 전파합니다.

`ManagedContextElement`는 `CopyableThreadContextElement`를 구현합니다. 자식 코루틴이 시작될 때 `copyForChild()`가 자동으로 호출되어, 각 자식이 독립적인 span 트리를 가진 **포크된** 컨텍스트를 받습니다.

```kotlin
launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    // 포크된 컨텍스트: traceId 상속, 독립적인 span 트리
    withContext(Dispatchers.Default) {
        Operations.context.traceId  // 모든 suspension point 이후 접근 가능
        Operations.context.executionScope  // ASYNC
    }
}
```

### 코루틴별 컨텍스트 격리

각 자식 코루틴은 독립적인 컨텍스트를 가집니다. 병렬 코루틴은 서로의 span 트리를 공유하거나 간섭하지 않습니다.

```kotlin
coroutineScope {
    async {
        // 포크된 컨텍스트 A — async.execution을 루트로 하는 독립적인 span 트리
        checkInventory(request)
    }
    async {
        // 포크된 컨텍스트 B — async.execution을 루트로 하는 독립적인 span 트리
        reservePayment(request)
    }
}
```

### 훅 실행

`@Async`와 동일한 설정이 적용됩니다. 훅은 코루틴의 `Job`이 완료될 때 `invokeOnCompletion`을 통해 자동으로 실행됩니다 — DSL 래퍼가 필요 없습니다.

```kotlin
// 이 요청에서 시작되는 코루틴에 훅 활성화
Operations.context.enableAsyncHook()

launch(Dispatchers.IO + ManagedContextElement(Operations.context)) {
    processEvent()  // 코루틴 완료 시 훅 실행
}
```

### 요약

| | 컨텍스트 접근 | Span 트리 | 훅 실행 |
|--|--|--|--|
| 메인 요청 스레드 | ✓ | ✓ 메인 트리 | ✓ 항상 (서블릿 필터) |
| `@Async` 스레드 | ✓ 포크 (`executionScope=ASYNC`) | ✓ 독립 | 선택적 — 설정 또는 `enableAsyncHook()` |
| 코루틴 자식 | ✓ 포크 (`executionScope=ASYNC`) | ✓ 독립 | 선택적 — 설정 또는 `enableAsyncHook()` |
| 이벤트 핸들러 (`@ManagedEventHandler`) | ✓ 신규 (`executionScope=EVENT`) | ✓ 독립 | ✓ 항상 |

---

## 이벤트 드리븐 컨텍스트 전파 (`@ManagedEventHandler`)

메시징 핸들러 메서드에 `@ManagedEventHandler`를 붙이면 ENTRY 레이어 span이 시작되고 `executionScope`가 `EVENT`로 설정됩니다. 서블릿 필터는 관여하지 않으며 Aspect가 컨텍스트 전체 생명주기를 관리합니다.

### 자동 컨텍스트 추출

OMK는 메서드 인자에서 다음 우선순위로 트레이스 메타데이터를 추출합니다:

| 우선순위 | 소스 | 조건 |
|----------|------|------|
| 1순위 | `@ManagedEvent*` 필드 어노테이션 | 인자 클래스의 필드에 `@ManagedEventTraceId`, `@ManagedEventCausationId`, `@ManagedEventIssuer`, `@ManagedEventType` 중 하나라도 있는 경우 |
| 2순위 | Kafka `ConsumerRecord` 헤더 | 인자가 `ConsumerRecord`인 경우 — W3C `traceparent` 우선 파싱(`00-{traceId}-{spanId}-{flags}`), 없으면 `X-Trace-Id` / `X-Causation-Id` |
| 3순위 | Spring `Message<*>` 헤더 | 인자가 `org.springframework.messaging.Message`를 구현하는 경우 — `MessageHeaders` 탐색 |
| 4순위 | 덕 타이핑 | 리플렉션으로 `traceId`, `causationId`, `issuer`, `eventType` 필드 또는 getter 탐색 |
| 5순위 | `generate-when-missing` | 컨텍스트를 찾지 못한 경우: 새 ID 생성(기본값 `true`) 또는 빈 문자열 주입(`false`) |

### 어노테이션 기반 추출 (최우선)

이벤트 또는 도메인 객체의 필드에 `@ManagedEvent*` 어노테이션을 붙입니다:

```kotlin
import io.github.hchanjune.omk.core.annotations.*

data class OrderCreatedEvent(
    @ManagedEventTraceId     val traceId: String,
    @ManagedEventCausationId val causationId: String,
    @ManagedEventIssuer      val issuer: String,
    @ManagedEventType        val eventType: String = "OrderCreated",
    val orderId: Long
)

@Component
class OrderEventHandler {

    @ManagedEventHandler
    @KafkaListener(topics = ["order.created"])
    fun handle(event: OrderCreatedEvent) {
        // Operations.context 사용 가능 — executionScope = EVENT
        // 어노테이션이 붙은 필드에서 traceId, causationId, issuer, eventType 자동 주입
    }
}
```

### Kafka `ConsumerRecord` (어노테이션 불필요)

핸들러가 `ConsumerRecord`를 직접 수신하는 경우 OMK가 헤더를 자동으로 읽습니다.

```kotlin
@Component
class OrderEventHandler {

    @ManagedEventHandler
    @KafkaListener(topics = ["order.created"])
    fun handle(record: ConsumerRecord<String, String>) {
        // W3C traceparent 헤더 읽음; 없으면 X-Trace-Id / X-Causation-Id로 폴백
        // eventType = 토픽 이름
    }
}
```

### 수동 초기화 (Outbox / Inbox 패턴)

Transactional Outbox / Inbox 패턴에서는 이벤트가 DB 테이블에 저장된 후 처리됩니다. 트레이스 메타데이터(`traceId`, `causationId` 등)는 Kafka 헤더가 아닌 DB 컬럼에 저장되어 있어 `@ManagedEventHandler` Aspect가 메서드 인자에서 자동으로 추출할 수 없습니다. 따라서 컨텍스트를 수동으로 초기화해야 합니다.

#### 동작 원리

`Operations.initializeForEvent()`를 어노테이션이 붙은 핸들러 메서드보다 **먼저** 호출하면, Aspect는 이미 컨텍스트가 존재함을 감지(`contextOwner = false`)하고 자동 추출을 건너뜁니다. ENTRY 레이어 span 생성과 메트릭 기록은 여전히 수행하지만, **라이프사이클(훅 실행 + clear)은 호출자가 책임**집니다.

따라서 폴링 메서드가 직접 처리해야 하는 것:
1. inbox 메타데이터로 `Operations.initializeForEvent()` 호출
2. **Spring 프록시를 통해** `handle()` 호출 (Aspect 인터셉트를 위해 — 아래 설명 참고)
3. 핸들러 반환 후 `Operations.complete()` 호출
4. `Operations.hook?.onSuccess()` / `Operations.hook?.onFailure()` 명시적 실행
5. `finally`에서 `Operations.clear()` 호출

#### 완전한 `AbstractEventHandler` 패턴

```kotlin
abstract class AbstractEventHandler<E : Event> : EventHandler<E>, ApplicationContextAware {

    private lateinit var applicationContext: ApplicationContext
    private lateinit var inboxStore: EventInboxStore
    private lateinit var eventSerializer: EventSerializer

    override fun setApplicationContext(ctx: ApplicationContext) {
        applicationContext = ctx
        inboxStore = ctx.getBean<EventInboxStore>()
        eventSerializer = ctx.getBean<EventSerializer>()
    }

    @Scheduled(fixedDelay = 500)
    open fun pollAndProcess() {
        inboxStore.findAllByEventTypeAndStatus(eventType, EventInboxStatus.RECEIVED, limit = 100)
            .filter { inboxStore.tryAcquire(it.eventId) }
            .forEach { processOne(it) }
    }

    protected fun processOne(record: EventInbox) {
        Operations.initializeForEvent(
            EventMetadata(
                traceId     = record.traceId,
                causationId = record.causationId,
                issuer      = record.issuer,
                eventType   = record.eventType
            )
        )
        val context = Operations.context
        try {
            val event = eventSerializer.deserialize(record.payload, eventClass.java)
            // Spring 프록시를 통해 호출해야 @ManagedEventHandler Aspect가 동작함
            applicationContext.getBean(this::class.java).handle(event)
            Operations.complete()
            Operations.hook?.onSuccess(context)
            inboxStore.markCompleted(record.eventId)
        } catch (e: Exception) {
            Operations.complete()
            Operations.hook?.onFailure(context, e)
            inboxStore.markFailed(record.eventId, e.message ?: "")
        } finally {
            Operations.clear()
        }
    }
}
```

#### 구체적인 핸들러

```kotlin
@Component
class OrderCreatedEventHandler : AbstractEventHandler<OrderCreatedEvent>() {

    override val eventType: String = "order.created"
    override val eventClass: KClass<OrderCreatedEvent> = OrderCreatedEvent::class

    @ManagedEventHandler
    override fun handle(event: OrderCreatedEvent) {
        // Operations.context 사용 가능 — executionScope = EVENT
        // traceId, causationId, issuer는 inbox 레코드에서 주입됨
    }
}
```

#### `applicationContext.getBean(this::class.java)`가 필요한 이유

`processOne()`은 `handle()`을 내부에서 호출합니다. Spring AOP는 프록시를 통해 동작하기 때문에, `this.handle()`로 직접 호출하면 프록시를 우회해 `@ManagedEventHandler` Aspect가 실행되지 않습니다. ApplicationContext에서 빈을 꺼내면 프록시 참조가 반환되어 Aspect가 정상적으로 인터셉트합니다.

> `contextOwner = false` 상태에서 Aspect는 span 관리만 담당합니다(`Operations.complete()`, 훅 실행, `Operations.clear()` 미실행). 이 세 가지는 반드시 폴링 메서드에서 직접 처리해야 합니다.

### Span 트리

```
@ManagedEventHandler  ──  [ENT] 루트 span  (executionScope = EVENT)
    └── @ManagedOperation  ──  [APP] 자식 span
            └── @ManagedMetric      ──  [APP] 자식 span
            └── @ManagedRepository  ──  [DB]  자식 span
```

---

## 스레드 생명주기와 컨텍스트 격리

async 경계는 공유 참조가 아닌 **포크된** 컨텍스트를 생성합니다.

**메인 스레드 생명주기 (HTTP)**

```
요청 수신 → 컨텍스트 생성 → 비즈니스 로직 → 훅 실행 → clear() → 응답 반환
```

**Async/코루틴 생명주기**

```
메인 스레드:   [컨텍스트 A] ──── 비즈니스 로직 ──── 훅 실행 ──── clear()
                      │ forkAsync()
Async 스레드:          └─── [컨텍스트 B: executionScope=ASYNC] ──── async 작업 ──── (훅, 활성화 시) ──── clear()
```

**이벤트 핸들러 생명주기**

```
메시지 수신 → 컨텍스트 생성 (executionScope=EVENT) → 핸들러 로직 → 훅 실행 → clear()
```

두 async 컨텍스트는 로그 상관관계를 위해 `traceId`와 `causationId`를 공유하지만, span 트리, 타이밍, 훅 라이프사이클은 완전히 독립적입니다. 공유 상태에 대한 동시 변경이 없습니다 — 각 스레드는 자신만의 컨텍스트 인스턴스에서 동작합니다.

---

## Spring Security 연동

Spring Security가 클래스패스에 있으면, 보안 컨텍스트에서 자동으로 발급자를 추출합니다.

```
issuer = authentication.name  # 인증된 사용자
issuer = "anonymous"          # 미인증 사용자
```

Spring Security는 **선택적** 의존성입니다. 없어도 라이브러리는 정상 동작합니다.

---

## 관측 파이프라인 구조

OMK는 동일한 span 트리를 두 개의 **독립적인** 파이프라인으로 내보냅니다:

```
OMK span 트리 (요청 완료 시점에 확정)
    │
    ├── MetricsOperationHook  →  Micrometer MeterRegistry  →  Prometheus / Grafana Mimir
    │
    └── OtelOperationHook     →  OpenTelemetry Tracer      →  Tempo / Jaeger / Zipkin
```

두 파이프라인은 서로 독립적입니다. 둘 다 켜거나, 하나만 켜거나, 둘 다 끌 수 있습니다.

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

> Prometheus는 **pull 방식**입니다. 라이브러리가 메트릭을 밀어 보내는 게 아니라, Prometheus가 주기적으로 `/actuator/prometheus` 엔드포인트를 scrape합니다. 해당 엔드포인트가 Prometheus 인스턴스에서 접근 가능한지 확인하세요.

---

## OpenTelemetry 연동

`io.opentelemetry.api.trace.Tracer` Bean이 클래스패스에 있으면 `OtelOperationHook`이 자동으로 활성화됩니다. 요청이 완료된 시점에 OMK span 트리를 순회하며 각 span을 OTel SDK로 export하고, SDK가 설정된 백엔드(Tempo, Jaeger, Zipkin 등)로 전송합니다.

### Span 내보내기 방식

각 `MetricSpan`은 다음 정보를 가진 OTel span으로 변환됩니다:
- **시작/종료 타임스탬프**: OMK 타이밍 기반 (요청 완료 후 일괄 export — 실시간이 아님)
- **Attributes**: `MetricTags` 전체 (entrypoint, service, operation, repository 등)
- **SpanKind**: `MetricLayer` 기반 — `ENTRY → SERVER`, `APPLICATION → INTERNAL`, `DB/EXTERNAL → CLIENT`
- **Status**: `MetricOutcome` 기반 — `SUCCESS → OK`, 그 외 → `ERROR` (에러 타입 포함)
- **부모 컨텍스트**: 수신된 `traceId` / `causationId` (W3C 형식)로 재구성 — 업스트림 서비스의 분산 트레이스와 연결됨

### 설정 — 최소 구성 (단독 OTel)

```kotlin
// build.gradle.kts
implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
```

```yaml
# application.yml
management:
  otlp:
    tracing:
      endpoint: http://tempo:4318/v1/traces   # 또는 Jaeger / Zipkin 엔드포인트
  tracing:
    sampling:
      probability: 1.0
```

starter가 `Tracer` Bean을 자동 등록하고, `OtelOperationHook`이 `@ConditionalOnBean`으로 감지해 활성화됩니다.

### 설정 — Spring Boot Micrometer Tracing과 함께 사용

HTTP 자동 계측을 위해 Micrometer Tracing을 이미 사용하는 경우, OTel bridge를 추가하면 `Tracer` Bean도 함께 등록됩니다:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
```

이 경우 Micrometer Tracing이 HTTP server span을 자동 생성하고, OMK span은 동일 업스트림 `traceparent`를 기반으로 별도의 트레이스 루트로 시작됩니다. Tempo/Jaeger에서 같은 분산 트레이스 내에서 함께 확인할 수 있습니다.

### 백엔드별 엔드포인트

| 백엔드 | OTLP 엔드포인트 |
|--------|----------------|
| Grafana Tempo | `http://tempo:4318/v1/traces` |
| Jaeger | `http://jaeger:4317/v1/traces` (gRPC) 또는 `http://jaeger:4318/v1/traces` (HTTP) |
| Zipkin | OTLP 대신 `opentelemetry-exporter-zipkin` 사용 |

### OTel export 비활성화

```yaml
operation-manager:
  webmvc:
    otel:
      enabled: false
```

---

## Spring Logback 연동

OMK는 오퍼레이션 결과를 두 개의 전용 로거로 출력합니다:

| 로거 이름 | 내용 |
|-----------|------|
| `OperationManager.Pretty` | 사람이 읽기 좋은 박스 포맷 (개발용) |
| `OperationManager.JSON` | 구조화된 JSON (프로덕션 / 로그 수집) |

`OperationManager.JSON`은 오퍼레이션마다 `traceId`, `causationId`, `protocol`, `status`, `durationMs` 등을 포함한 일관된 JSON을 생성합니다. 이 구조와 가장 잘 맞는 프로덕션 스택은 **Loki + Grafana**입니다 — JSON 필드를 라벨과 스트림 필터로 추출해 별도의 APM 없이 오퍼레이션 메타데이터 기반의 대시보드와 알림을 구성할 수 있습니다.

두 로거는 `logback-spring.xml`에서 독립적으로 라우팅합니다.

### 콘솔 전용 (개발)

```xml
<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="OperationManager.JSON" level="OFF" additivity="false"/>
```

### Loki — Option A: Promtail (권장)

JSON 로거를 전용 파일에 쓰고, Promtail이 파일을 tail하여 Loki로 전송합니다. Promtail은 디스크에 버퍼링하고 Loki 장애 시 재전송하므로 로그 유실 위험이 없습니다.

```xml
<!-- logback-spring.xml -->
<appender name="OMK_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/omk-operations.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/omk-operations.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder>
        <pattern>%message%n</pattern>
    </encoder>
</appender>

<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="OperationManager.JSON" level="INFO" additivity="false">
    <appender-ref ref="OMK_FILE"/>
</logger>
```

Promtail을 `logs/omk-operations*.log`로 지정하면 파이프라인이 완성됩니다.

### Loki — Option B: `loki-logback-appender` (직접 push)

앱에서 Loki로 JSON 로그를 HTTP로 직접 전송합니다. Promtail이나 사이드카 없이 간단하게 구성할 수 있지만, Loki가 일시적으로 불가능한 경우 로그가 유실될 수 있습니다.

```kotlin
// build.gradle.kts
implementation("com.github.loki4j:loki-logback-appender:1.5.x")
```

```xml
<!-- logback-spring.xml -->
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
        <url>http://loki:3100/loki/api/v1/push</url>
    </http>
    <format>
        <label>
            <pattern>app=${APP_NAME},env=${ENV},level=%level</pattern>
        </label>
        <message>
            <pattern>%message</pattern>
        </message>
        <sortByTime>true</sortByTime>
    </format>
</appender>

<logger name="OperationManager.Pretty" level="INFO" additivity="false">
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="OperationManager.JSON" level="INFO" additivity="false">
    <appender-ref ref="LOKI"/>
</logger>
```

> `additivity="false"`는 두 로거 모두 필수입니다. 없으면 로그 이벤트가 root logger로 전파되어 콘솔/파일 appender에도 중복 출력됩니다.

### 비동기 Appender (고트래픽 환경)

고트래픽 환경에서는 Logback 내장 `AsyncAppender`로 기존 Appender를 감싸면 파일 I/O나 네트워크 I/O를 요청 스레드에서 분리할 수 있습니다:

```xml
<appender name="OMK_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <!-- ...기존 설정... -->
</appender>

<appender name="ASYNC_OMK" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="OMK_FILE"/>
    <queueSize>1000</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <includeCallerData>false</includeCallerData>
</appender>

<logger name="OperationManager.JSON" level="INFO" additivity="false">
    <appender-ref ref="ASYNC_OMK"/>
</logger>
```

| 옵션 | 설명 |
|------|------|
| `queueSize` | 인메모리 이벤트 버퍼 크기 (바이트가 아닌 이벤트 수). 버스트 트래픽을 흡수할 수 있도록 충분히 설정. |
| `discardingThreshold=0` | 기본값은 큐가 80% 이상 차면 INFO 로그를 자동으로 버림. `0`으로 설정하면 비활성화되어 로그 유실을 방지. |
| `includeCallerData=false` | 스택 프레임 메타데이터 수집 비활성화 — 비용이 큰 작업이므로 반드시 꺼야 함. |

**실제로 완화되는 부분:** I/O 비용 — 파일 쓰기와 네트워크 호출(Loki 직접 push). `loki-logback-appender` 방식에서 효과가 가장 큽니다. 요청마다 1~10ms의 네트워크 레이턴시가 요청 스레드를 블로킹하던 것이 제거됩니다.

**여전히 요청 스레드에서 실행되는 것:** 로그 메시지 문자열은 비동기 큐에 넘기기 전에 동기적으로 빌드됩니다. 대부분의 워크로드에서는 무시할 수 있는 수준이지만, span 트리가 깊은 요청에서는 문자열 빌드 비용이 남습니다.

### 환경별 권장 설정

| 환경 | Pretty | JSON |
|------|--------|------|
| 로컬 / 개발 | `CONSOLE` | `OFF` |
| 스테이징 | `CONSOLE` | `LOKI` (두 방식 모두 가능) |
| 프로덕션 | `OFF` | `LOKI` + `AsyncAppender` (Promtail 권장) |

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

- **스레드 로컬 컨텍스트**: `ManagedContext`는 `ThreadLocal`에 저장됩니다. `@Async` 전파는 `ManagedContextTaskDecorator`를 통해 자동으로 포크됩니다. Kotlin 코루틴은 `ManagedContextElement`를 통한 명시적 전파가 필요합니다. 포크된 async 컨텍스트는 독립적인 span 트리와 생명주기를 가지며, 훅 실행은 설정이나 `enableAsyncHook()`으로 제어합니다.
- **Spring AOP 자기 호출**: 동일 클래스 내부의 메서드 호출은 AOP Aspect가 인터셉트하지 않습니다.
- **스트리밍 응답**: `traceparent` 응답 헤더는 요청 처리 완료 후 설정됩니다. 스트리밍 또는 비동기 응답에서는 헤더가 전달되지 않을 수 있습니다.
- **`Operations.context` 범위**: 관리 범위(HTTP 요청, 이벤트 핸들러, 또는 수동 `Operations.initializeForEvent()`) 밖에서 `Operations.context`를 호출하면 명확한 메시지와 함께 `IllegalStateException`이 발생합니다.
- **샘플링**: OMK는 별도의 샘플링 레이어를 구현하지 않습니다. 각 관심사는 해당 계층에서 처리됩니다 — 트레이스 양은 OTel SDK(`management.tracing.sampling.probability`)로 제어하고, 로그 I/O 오버헤드는 Logback `AsyncAppender`로 제거하며, Micrometer 기록은 설계상 요청당 오버헤드가 없습니다. 성공 요청 로그 비활성화(`success-level: NONE`)와 `AsyncAppender` 적용으로 대부분의 고트래픽 환경의 오버헤드를 해결할 수 있습니다(주의사항 참조).

---

## 로드맵

- [ ] Maven Central 배포
- [ ] WebFlux 지원
- [x] 비동기 컨텍스트 전파 (`@Async`는 `ManagedContextTaskDecorator`, 코루틴은 `ManagedContextElement`)
- [x] Span 수준 메트릭 계측 (`@ManagedOperation`, `@ManagedMetric`, `@ManagedRepository` DB span)
- [x] Micrometer 기반 `MetricsOperationHook` 및 전체 span 트리 기록
- [x] `@ManagedController`의 ENTRY 레이어 루트 span; span 트리에 레이어/타임스탬프/스레드명 표시
- [x] 메시징 컨텍스트 전파 (`@ManagedEventHandler` — Kafka, Spring Messaging 등; 자동 추출 우선순위 체인; 수동 `Operations.initializeForEvent()`)
- [x] OpenTelemetry SDK 연동 (`OtelOperationHook` — `Tracer` Bean 감지 시 자동 활성화)
- [x] 샘플링 — 계층별 처리: OTel SDK로 트레이스 샘플링, `AsyncAppender`로 로그 I/O, `success-level: NONE`으로 로그 볼륨 제어 (주의사항 참조)

---

## 라이선스

이 프로젝트는 오픈소스입니다. 기여를 환영합니다.
