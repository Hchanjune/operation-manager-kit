# OMK — API 레퍼런스

**한국어 | [English](API.md)**

→ [README로 돌아가기](README.ko.md)

---

## 어노테이션

### `@ManagedController`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedController
```

`@RestController` 또는 `@Controller` 클래스에 적용합니다. AOP Aspect가 모든 핸들러 메서드를 인터셉트하여 호출마다 **ENTRY 레이어** 루트 span을 생성합니다.

---

### `@ManagedService`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedService
```

`@Service` 클래스에 적용합니다. 클래스명을 `ManagedContext.service`에 주입합니다.

---

### `@ManagedRepository`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedRepository
```

`@Repository` 클래스에 적용합니다. 모든 메서드를 **DB 레이어** 자식 span으로 계측합니다.

> Spring Data 리포지토리 인터페이스(`JpaRepository`, `CoroutineCrudRepository` 등)에는 직접 적용할 수 없습니다. 대신 서비스 메서드에 `@ManagedMetric`을 사용하세요.

---

### `@ManagedOperation`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedOperation(
    val operation: String = "",
    val useCase: String = ""
)
```

서비스 메서드에 적용합니다. `operation`과 `useCase`를 컨텍스트에 주입하고 **APPLICATION 레이어** span을 생성합니다.

| 파라미터        | 타입       | 기본값  | 설명                          |
|-------------|----------|------|-----------------------------|
| `operation` | `String` | `""` | 오퍼레이션명 (예: `"CreateOrder"`) |
| `useCase`   | `String` | `""` | 유스케이스명 (예: `"PlaceOrder"`)  |

---

### `@ManagedMetric`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedMetric(
    val name: String = ""
)
```

임의의 메서드에 적용합니다. 주어진 이름으로 **APPLICATION 레이어** 자식 span을 생성합니다. `name`이 비어있으면 `ClassName.methodName`을 사용합니다.

| 파라미터   | 타입       | 기본값  | 설명                                       |
|--------|----------|------|------------------------------------------|
| `name` | `String` | `""` | span 이름. 비어있으면 `ClassName.methodName` 사용 |

---

### `@ManagedEventHandler`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedEventHandler
```

메시징 핸들러 메서드(Kafka, Spring Messaging 등)에 적용합니다. **ENTRY 레이어** span을 생성하고 `executionScope`를 `EVENT`로 설정합니다. 메서드 인자에서 트레이스 메타데이터를 자동 추출합니다.

---

### 이벤트 필드 어노테이션

이벤트/도메인 객체의 필드에 적용하여 트레이스 컨텍스트를 자동 추출합니다.

| 어노테이션                      | 필드 타입    | 추출 대상                        |
|----------------------------|----------|------------------------------|
| `@ManagedEventTraceId`     | `String` | `ManagedContext.traceId`     |
| `@ManagedEventCausationId` | `String` | `ManagedContext.causationId` |
| `@ManagedEventIssuer`      | `String` | `ManagedContext.issuer`      |
| `@ManagedEventType`        | `String` | `ManagedContext.type`        |

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

단일 오퍼레이션 라이프사이클의 모든 메타데이터를 담는 핵심 객체입니다.

```kotlin
class ManagedContext
```

### 읽기 전용 필드

| 필드               | 타입                    | 설명                                        |
|------------------|-----------------------|-------------------------------------------|
| `traceId`        | `String`              | 분산 트레이스 ID (W3C 또는 커스텀)                   |
| `causationId`    | `String`              | 부모 span / 인과관계 ID                         |
| `issuer`         | `String`              | 인증된 사용자명, 또는 `"anonymous"`                |
| `protocol`       | `ManagedProtocolType` | `HTTP`, `MESSAGING`, `RPC`, `DB` 등        |
| `type`           | `String`              | 오퍼레이션 타입: `"API"`, `"EVENT"`, `"BATCH"` 등 |
| `uri`            | `String`              | HTTP 요청 URI                               |
| `method`         | `String`              | HTTP 메서드 (`"GET"`, `"POST"` 등)            |
| `entrypoint`     | `String`              | 컨트롤러 클래스명                                 |
| `service`        | `String`              | 서비스 클래스명                                  |
| `operation`      | `String`              | `@ManagedOperation.operation` 값           |
| `useCase`        | `String`              | `@ManagedOperation.useCase` 값             |
| `response`       | `String`              | `Operations { }` 반환값의 `toString()`        |
| `timestamp`      | `Instant`             | 컨텍스트 생성 시간 (UTC)                          |
| `durationMs`     | `Long`                | 총 실행 시간 (밀리초)                             |
| `executionScope` | `ExecutionScope`      | `PRIMARY`, `ASYNC`, 또는 `EVENT`            |
| `rootSpan`       | `MetricSpan?`         | span 트리의 루트. 첫 span이 push되기 전엔 `null`     |
| `hookRecords`    | `List<HookRecord>`    | 각 훅의 실행 결과 목록                             |

### 수정 가능한 필드

| 필드                   | 타입        | 설명                                                           |
|----------------------|-----------|--------------------------------------------------------------|
| `message`            | `String`  | 자유 형식 레이블. `DefaultOperationLoggingHook`(Order 50) 이전 훅에서 설정 |
| `isAsyncHookEnabled` | `Boolean` | async 자식 컨텍스트에서 훅 실행 여부                                      |

### 메서드

| 메서드                  | 설명                            |
|----------------------|-------------------------------|
| `enableAsyncHook()`  | `@Async` / 코루틴 자식에서 훅 실행 활성화  |
| `disableAsyncHook()` | `@Async` / 코루틴 자식에서 훅 실행 비활성화 |

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

Servlet 스택용 전역 싱글톤. 스레드 로컬 컨텍스트 접근자입니다.

```kotlin
object Operations
```

### 프로퍼티

| 프로퍼티         | 타입               | 설명                                                 |
|--------------|------------------|----------------------------------------------------|
| `context`    | `ManagedContext` | 현재 컨텍스트. 관리 범위 밖에서 호출하면 `IllegalStateException` 발생 |
| `hasContext` | `Boolean`        | 현재 스레드에 컨텍스트가 있으면 `true`                           |
| `hook`       | `OperationHook?` | CompositeOperationHook 인스턴스                        |

### invoke 연산자

```kotlin
operator fun <T> invoke(block: ManagedContext.() -> T): OperationResult<T>
```

현재 컨텍스트 내에서 `block`을 실행하고, 반환값을 `OperationResult.data`로 캡처하며 `response`를 컨텍스트에 주입합니다. `this`는 `ManagedContext`입니다.

```kotlin
val result = Operations {
    // this: ManagedContext
    val order = repo.save(Order.from(request))
    OrderResponse.from(order)           // result.data로 캡처됨
}
result.data    // OrderResponse
result.context // ManagedContext
```

### 정적 메서드

| 메서드                                           | 설명                                |
|-----------------------------------------------|-----------------------------------|
| `initializeForEvent(metadata: EventMetadata)` | 메시징 컨텍스트 수동 초기화 (Outbox/Inbox 패턴) |
| `complete()`                                  | 컨텍스트 타이머 종료                       |
| `clear()`                                     | `ThreadLocal`에서 컨텍스트 제거           |

---

## ReactiveOperations (WebFlux)

`Operations`의 리액티브 버전. Reactor 컨텍스트에서 컨텍스트를 읽습니다.

```kotlin
object ReactiveOperations
```

### suspend invoke 연산자

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

### Mono 헬퍼

```kotlin
fun <T : Any> mono(block: ManagedContext.() -> Mono<T>): Mono<OperationResult<T>>
```

```kotlin
val result: Mono<OperationResult<MyData>> = ReactiveOperations.mono {
    Mono.just(MyData())
}
```

### 프로퍼티

| 프로퍼티   | 타입               | 설명                          |
|--------|------------------|-----------------------------|
| `hook` | `OperationHook?` | CompositeOperationHook 인스턴스 |

---

## OperationHook

라이프사이클 콜백 인터페이스입니다.

```kotlin
interface OperationHook {
    fun onSuccess(context: ManagedContext) {}
    fun onFailure(context: ManagedContext, exception: Throwable) {}
}
```

Spring `@Component`로 등록하고, `@Order`로 순서를 제어합니다.

```kotlin
@Component
@Order(30)
class MyHook : OperationHook {
    override fun onSuccess(context: ManagedContext) { ... }
    override fun onFailure(context: ManagedContext, exception: Throwable) { ... }
}
```

내장 훅:

| 훅                             | Order | 설명                                          |
|-------------------------------|-------|---------------------------------------------|
| `DefaultOperationLoggingHook` | 50    | 오퍼레이션 결과 로깅 (pretty / JSON)                 |
| `MetricsOperationHook`        | 60    | span 트리를 Micrometer에 기록                     |
| `OtelOperationHook`           | 70    | OpenTelemetry로 span 내보내기 (`Tracer` Bean 필요) |

---

## OperationResult\<T\>

```kotlin
data class OperationResult<T>(
    val context: ManagedContext,
    val data: T
)
```

`Operations { }` 및 `ReactiveOperations { }` 실행 결과입니다.

---

## MetricSpan

span 트리의 개별 계측 단위입니다.

```kotlin
class MetricSpan
```

| 필드                 | 타입                 | 설명                                |
|--------------------|--------------------|-----------------------------------|
| `traceId`          | `String`           | 컨텍스트에서 상속한 트레이스 ID                |
| `spanId`           | `String`           | 고유 span ID                        |
| `name`             | `MetricName`       | span 이름                           |
| `threadName`       | `String`           | span을 시작한 스레드명                    |
| `startTime`        | `Long?`            | 시작 시간 (epoch 밀리초)                 |
| `durationMs`       | `Long?`            | 소요 시간 (밀리초). `end()` 호출 전엔 `null` |
| `outcome`          | `MetricOutcome?`   | 실행 결과. 종료 전엔 `null`               |
| `descriptor.layer` | `MetricLayer`      | `ENTRY`, `APPLICATION`, 또는 `DB`   |
| `children`         | `List<MetricSpan>` | 자식 span 목록                        |
| `parent`           | `MetricSpan?`      | 부모 span                           |

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

| 값                 | 조건                                                  |
|-------------------|-----------------------------------------------------|
| `SUCCESS`         | 정상 완료                                               |
| `FAILURE_CLIENT`  | `IllegalArgumentException`, `IllegalStateException` |
| `FAILURE_SERVER`  | 그 외 예외                                              |
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

`Operations.initializeForEvent()` 및 `ReactiveOperations.initializeForEvent()`와 함께 사용합니다 (Outbox/Inbox 패턴 수동 초기화).

---

## ExecutionScope

```kotlin
enum class ExecutionScope {
    PRIMARY,  // 메인 HTTP / 이벤트 루프 스레드
    ASYNC,    // @Async 스레드 또는 포크된 코루틴
    EVENT     // @ManagedEventHandler
}
```

---

## ManagedProtocolType

```kotlin
enum class ManagedProtocolType {
    HTTP, RPC, MESSAGING, FAAS, DB, UNSUPPORTED
}
```

---

## 프로바이더 인터페이스

동일한 인터페이스의 `@Bean`을 등록하면 기본 구현을 교체할 수 있습니다.

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

기본 동작:
- **WebMVC**: `SecurityContextHolder`에서 읽거나 `"anonymous"` 폴백
- **WebFlux**: `ReactiveSecurityContextHolder`에서 읽거나 `"anonymous"` 폴백

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

기본 구현은 Micrometer를 사용합니다. `MeterRegistry`가 없으면 no-op으로 폴백합니다.

---

## 설정 프로퍼티

### WebMVC — `operation-manager.webmvc`

| 프로퍼티                                                | 타입                | 기본값              | 설명                                 |
|-----------------------------------------------------|-------------------|------------------|------------------------------------|
| `context-filter.enabled`                            | `Boolean`         | `true`           | 서블릿 필터 활성화                         |
| `context-aspect.enabled`                            | `Boolean`         | `true`           | AOP Aspect 활성화                     |
| `micrometer.enabled`                                | `Boolean`         | `true`           | Micrometer 기록 활성화                  |
| `otel.enabled`                                      | `Boolean`         | `true`           | OTel export 활성화 (`Tracer` Bean 필요) |
| `logging.enabled`                                   | `Boolean`         | `true`           | 기본 로깅 훅 활성화                        |
| `logging.pretty`                                    | `Boolean`         | `false`          | Pretty-print 포맷                    |
| `logging.json`                                      | `Boolean`         | `true`           | JSON 포맷                            |
| `logging.spans`                                     | `Boolean`         | `false`          | pretty 출력에 span 트리 포함              |
| `logging.response`                                  | `Boolean`         | `true`           | 오퍼레이션 반환값을 로그에 포함                  |
| `logging.success-level`                             | `LogLevel`        | `INFO`           | 성공 시 로그 레벨                         |
| `logging.failure-level`                             | `LogLevel`        | `ERROR`          | 실패 시 로그 레벨                         |
| `async-propagation.enabled`                         | `Boolean`         | `true`           | `@Async` 컨텍스트 전파 활성화               |
| `async-propagation.hook-enabled`                    | `Boolean`         | `false`          | async 태스크 완료 시 훅 실행                |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` 또는 `CUSTOM`         |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | 요청 헤더에 ID가 없을 때 자동 생성              |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | 커스텀 트레이스 ID 헤더명                    |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | 커스텀 인과관계 ID 헤더명                    |

### WebFlux — `operation-manager.webflux`

| 프로퍼티                                                | 타입                | 기본값              | 설명                         |
|-----------------------------------------------------|-------------------|------------------|----------------------------|
| `context-filter.enabled`                            | `Boolean`         | `true`           | WebFilter 활성화              |
| `context-aspect.enabled`                            | `Boolean`         | `true`           | AOP Aspect 활성화             |
| `micrometer.enabled`                                | `Boolean`         | `true`           | Micrometer 기록 활성화          |
| `logging.enabled`                                   | `Boolean`         | `true`           | 기본 로깅 훅 활성화                |
| `logging.pretty`                                    | `Boolean`         | `false`          | Pretty-print 포맷            |
| `logging.json`                                      | `Boolean`         | `true`           | JSON 포맷                    |
| `logging.spans`                                     | `Boolean`         | `false`          | pretty 출력에 span 트리 포함      |
| `logging.response`                                  | `Boolean`         | `true`           | 오퍼레이션 반환값을 로그에 포함          |
| `logging.success-level`                             | `LogLevel`        | `INFO`           | 성공 시 로그 레벨                 |
| `logging.failure-level`                             | `LogLevel`        | `ERROR`          | 실패 시 로그 레벨                 |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` 또는 `CUSTOM` |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | 요청 헤더에 ID가 없을 때 자동 생성      |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | 커스텀 트레이스 ID 헤더명            |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | 커스텀 인과관계 ID 헤더명            |

---