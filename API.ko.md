# OMK — API 레퍼런스

**한국어 | [English](API.md)**

→ [README로 돌아가기](README.ko.md)

---

## 목차

- [어노테이션](#어노테이션)
  - [`@ManagedController`](#managedcontroller)
  - [`@ManagedService`](#managedservice)
  - [`@ManagedRepository`](#managedrepository)
  - [`@ManagedCacheRepository`](#managedcacherepository)
  - [`@ManagedOperation`](#managedoperation)
  - [`@ManagedMetric`](#managedmetric)
  - [`@ManagedEventHandler`](#managedeventhandler)
  - [`@ManagedSchedule`](#managedschedule)
  - [이벤트 필드 어노테이션](#이벤트-필드-어노테이션)
- [ManagedContext](#managedcontext)
- [Operations (WebMVC)](#operations-webmvc)
- [ReactiveOperations (WebFlux)](#reactiveoperations-webflux)
- [OperationHook](#operationhook)
- [OperationResult\<T\>](#operationresultt)
- [MetricSpan](#metricspan)
- [MetricOutcome](#metricoutcome)
- [EventMetadata](#eventmetadata)
- [ExecutionScope](#executionscope)
- [ManagedProtocolType](#managedprotocoltype)
- [OperationOutcome](#operationoutcome)
- [프로바이더 인터페이스](#프로바이더-인터페이스)
- [설정 프로퍼티](#설정-프로퍼티)

---

## 어노테이션

> 클래스 레벨 어노테이션(`@ManagedController`, `@ManagedService`, `@ManagedRepository`, `@ManagedCacheRepository`)은 전부 `@Inherited` — abstract 베이스 클래스에 붙이면 서브클래스가 선언한 메서드도 커버됩니다. 메서드 레벨 어노테이션은 자바 표준 시맨틱을 따릅니다: 선언된 메서드에 적용되며(오버라이드 없이 상속된 경우 포함), 오버라이드하면 어노테이션을 다시 붙여야 합니다.

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

### `@ManagedCacheRepository`

```kotlin
@Target(AnnotationTarget.CLASS)
annotation class ManagedCacheRepository(val description: String = "")
```

캐시 접근 클래스(예: Redis 기반 캐시 리포지토리)에 적용합니다. 모든 메서드를 **CACHE 레이어** 자식 span으로 계측합니다 — 동작은 `@ManagedRepository`와 동일하지만 `[DB ]` 대신 `[CAC]`로 표시되어 span 트리와 메트릭에서 캐시 트래픽을 DB 트래픽과 구분할 수 있습니다.

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

### `@ManagedSchedule`

```kotlin
@Target(AnnotationTarget.FUNCTION)
annotation class ManagedSchedule(
    val description: String = "",
    val quietWhenEmpty: Boolean = false
)
```

스케줄러로 시작되는 메서드(예: `@Scheduled`)에 적용합니다. 스케줄 실행은 트레이스 컨텍스트를 전달할 요청/메시지가 없으므로, 새로 생성한 `traceId`/`causationId`로 **ENTRY 레이어** span을 열고 `executionScope`를 `SCHEDULED`, `protocol`/`type`을 `SCHEDULED`로 설정합니다.

| 파라미터             | 타입        | 기본값     | 설명       |
|------------------|-----------|---------|----------|
| `description`    | `String`  | `""`    | 설명용 텍스트 |
| `quietWhenEmpty` | `Boolean` | `false` | 메서드 반환값이 "빈" 값(null, `Unit`, `0`, `false`, 빈 Collection/Map/Array/CharSequence)이면 기본 성공 로그를 침묵. 고빈도 폴러에서 처리 건수나 배치를 반환하게 하면 의미 있는 실행만 로깅됨. 실패는 항상 로깅되고 span/메트릭은 그대로 기록 |

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
| `ip`             | `String`              | 클라이언트 IP (`X-Forwarded-For` 첫 값, 없으면 remote address) |
| `deviceId`       | `String`              | `"NOT_SUPPORTED_YET"` — 예약됨, 아직 채워지지 않음     |
| `deviceInfo`     | `String`              | `"NOT_SUPPORTED_YET"` — 예약됨, 아직 채워지지 않음     |
| `protocol`       | `ManagedProtocolType` | `HTTP`, `MESSAGING`, `RPC`, `DB` 등        |
| `type`           | `String`              | 오퍼레이션 타입: `"API"`, `"EVENT"`, `"BATCH"` 등 |
| `uri`            | `String`              | HTTP 요청 URI                               |
| `method`         | `String`              | HTTP 메서드 (`"GET"`, `"POST"` 등)            |
| `entrypoint`     | `String`              | 컨트롤러 클래스명                                 |
| `service`        | `String`              | 서비스 클래스명                                  |
| `operation`      | `String`              | `@ManagedOperation.operation` 값           |
| `useCase`        | `String`              | `@ManagedOperation.useCase` 값             |
| `response`       | `String`              | `Operations { }` 반환값의 `toString()`        |
| `statusCode`     | `Int?`                | HTTP 응답 상태 코드. 응답 커밋 시점에 주입되기 전까지는 `null` |
| `outcome`        | `OperationOutcome`    | `statusCode`로부터 도출된 분류. 주입 전까지는 `SUCCESS` |
| `timestamp`      | `Instant`             | 컨텍스트 생성 시간 (UTC)                          |
| `durationMs`     | `Long`                | 총 실행 시간 (밀리초)                             |
| `executionScope` | `ExecutionScope`      | `PRIMARY`, `ASYNC`, 또는 `EVENT`            |
| `rootSpan`       | `MetricSpan?`         | span 트리의 루트. 첫 span이 push되기 전엔 `null`     |
| `hookRecords`    | `List<HookRecord>`    | 각 훅의 실행 결과 목록                             |
| `capturedException` | `Throwable?`       | `@ExceptionHandler`/`@ControllerAdvice`가 응답으로 변환하기 전에 기록된 진짜 예외 — webmvc는 `ExceptionCapturingResolver`, webflux는 `ManagedControllerAspect`가 기록함. 잡힌 예외가 없으면 `null`. 가장 먼저 기록된 예외가 유지됨 |

### 수정 가능한 필드

| 필드                   | 타입        | 설명                                                           |
|----------------------|-----------|--------------------------------------------------------------|
| `message`            | `String`  | 자유 형식 레이블. `DefaultOperationLoggingHook`(Order 50) 이전 훅에서 설정 |
| `defaultLogging`     | `Boolean` | `Operations { }` 블록 안에서 `false`로 설정하면 해당 실행의 기본 성공 로그만 침묵. 실패/클라이언트 에러/캡처된 예외는 항상 로깅됨. 자주 도는 `@ManagedSchedule` 잡 같은 시끄러운 경로에 유용 (예: `defaultLogging = processed > 0`) |
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
| `context`    | `ManagedContext` | 현재 컨텍스트. 관리 범위 밖에서 호출하면 실패하는 대신 WARN 로그 후 detached(비관리) 컨텍스트를 반환 — 해당 실행의 span/훅은 기록되지 않음 |
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

`onFailure`는 응답 상태가 `5xx`이거나 핸들러가 예외를 던진 경우에만 호출됩니다. `401`/`403`/그 외 `4xx`를 포함한 나머지 응답은 모두 `onSuccess`가 호출되므로, 이를 진짜 `SUCCESS`와 구분하려면 `context.outcome`([OperationOutcome](#operationoutcome) 참고)을 확인하세요.

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

| 훅                             | Order | 설명                          |
|-------------------------------|-------|------------------------------|
| `DefaultOperationLoggingHook` | 50    | 오퍼레이션 결과 로깅 (pretty / JSON) |
| `MetricsOperationHook`        | 60    | span 트리를 Micrometer에 기록     |

---

## SpanBridge (OpenTelemetry 라이브 브릿지)

OpenTelemetry 연동은 훅이 **아니다**: `Tracer` Bean이 있으면 `OperationRuntime`에 라이브
`SpanBridge`가 부착된다. 이후 모든 `ManagedContext.push()`는 **그 시점에 진짜 OTel span을
시작**하고, OTel이 생성한 id를 OMK에 역채택한다 — OMK 로그에 찍히는 `spanId`/`traceId`가
곧 트레이스 뷰어에서 검색하는 id다.

- 인바운드 `traceparent`(W3C 모드)가 있으면 업스트림 트레이스를 잇고, 없으면 OTel이 생성한
  traceId를 컨텍스트에 채택한다.
- Servlet: span이 스레드별 OTel *current context*로도 설치되어, OTel 자동계측 클라이언트
  (JDBC, `RestClient`, ...)가 OMK span 아래에 중첩된다.
- Reactive: span의 OTel context가 Reactor context로 전파되어
  (`opentelemetry-reactor-3.1` 선택 의존성), 계측된 reactive 클라이언트(`WebClient`, R2DBC, ...)가
  OMK span 아래에 중첩된다.
- `Tracer` Bean이 없거나 OTel이 클래스패스에 없으면 → 브릿지 없음; OMK가 자체 id를 생성하며
  완전히 독립 동작한다. 브릿지 오류는 비즈니스 흐름을 절대 깨지 않는다(fail-open).

```kotlin
interface SpanBridge {
    fun startTrace(context: ManagedContext): BridgedTrace
    fun startSpan(trace: BridgedTrace, name: String, layer: MetricLayer,
                  tags: MetricTags, parent: BridgedSpan?): BridgedSpan
    fun endSpan(handle: BridgedSpan, span: MetricSpan)
}
```

OTel 구현체는 `otel` 모듈(`OtelSpanBridge`)에 있고 servlet/reactive 스타터가 자동 설정한다.
애플리케이션 코드가 이 인터페이스를 직접 다룰 일은 없다.

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
| `descriptor.layer` | `MetricLayer`      | `ENTRY`, `APPLICATION`, `DB`, `CACHE`, 또는 `EXTERNAL` |
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
    PRIMARY,   // 메인 HTTP / 이벤트 루프 스레드
    ASYNC,     // @Async 스레드 또는 포크된 코루틴
    EVENT,     // @ManagedEventHandler
    SCHEDULED  // @ManagedSchedule
}
```

---

## ManagedProtocolType

```kotlin
enum class ManagedProtocolType {
    HTTP, RPC, MESSAGING, SCHEDULED, FAAS, DB, UNSUPPORTED
}
```

---

## OperationOutcome

```kotlin
enum class OperationOutcome {
    SUCCESS, UNAUTHENTICATED, FORBIDDEN, CLIENT_ERROR, SERVER_ERROR
}
```

HTTP 응답 상태 코드로부터 도출되는 분류로, WebMVC/WebFlux 필터가 success/failure 훅을 호출하기 직전에 `ManagedContext.injectStatusCode(statusCode)`를 통해 설정합니다.

| Outcome           | 상태 코드 범위 |
|-------------------|--------------|
| `SUCCESS`         | < 400        |
| `UNAUTHENTICATED` | 401          |
| `FORBIDDEN`       | 403          |
| `CLIENT_ERROR`    | 그 외 4xx     |
| `SERVER_ERROR`    | >= 500       |

`onFailure`는 `SERVER_ERROR`일 때만 호출됩니다. `UNAUTHENTICATED`/`FORBIDDEN`을 포함한 나머지 outcome은 모두 `onSuccess`가 호출되는데, 요청이 정상적으로 처리되어 의도한 응답(예: Spring Security의 `AuthenticationEntryPoint`가 작성한 401)이 반환되었기 때문입니다. `DefaultOperationLoggingHook`은 `onSuccess` 내부에서 `context.outcome`을 확인해, `SUCCESS`가 아닌 outcome은 `logging.success-level` 대신 `logging.client-error-level`로 로깅합니다.

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

### WebMVC — `operation-manager.servlet`

| 프로퍼티                                                | 타입                | 기본값              | 설명                                 |
|-----------------------------------------------------|-------------------|------------------|------------------------------------|
| `context-filter.enabled`                            | `Boolean`         | `true`           | 서블릿 필터 활성화                         |
| `context-aspect.enabled`                            | `Boolean`         | `true`           | AOP Aspect 활성화                     |
| `micrometer.enabled`                                | `Boolean`         | `true`           | Micrometer 기록 활성화                  |
| `otel.enabled`                                      | `Boolean`         | `true`           | 라이브 OTel span 브릿지 활성화 (`Tracer` Bean 필요) |
| `logging.enabled`                                   | `Boolean`         | `true`           | 기본 로깅 훅 활성화                        |
| `logging.pretty`                                    | `Boolean`         | `false`          | Pretty-print 포맷                    |
| `logging.json`                                      | `Boolean`         | `true`           | JSON 포맷                            |
| `logging.spans`                                     | `Boolean`         | `false`          | pretty 출력에 span 트리 포함              |
| `logging.response`                                  | `Boolean`         | `true`           | 오퍼레이션 반환값을 로그에 포함                  |
| `logging.success-level`                             | `LogLevel`        | `INFO`           | 성공 시 로그 레벨                         |
| `logging.failure-level`                             | `LogLevel`        | `ERROR`          | 실패 시 로그 레벨                         |
| `logging.client-error-level`                        | `LogLevel`        | `WARN`           | `outcome`이 `SUCCESS`가 아닌 `onSuccess` 호출(`UNAUTHENTICATED`, `FORBIDDEN`, `CLIENT_ERROR` 등)의 로그 레벨 |
| `async-propagation.enabled`                         | `Boolean`         | `true`           | `@Async` 컨텍스트 전파 활성화               |
| `async-propagation.hook-enabled`                    | `Boolean`         | `false`          | async 태스크 완료 시 훅 실행                |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` 또는 `CUSTOM`         |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | 요청 헤더에 ID가 없을 때 자동 생성              |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | 커스텀 트레이스 ID 헤더명                    |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | 커스텀 인과관계 ID 헤더명                    |

### WebFlux — `operation-manager.reactive`

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
| `logging.client-error-level`                        | `LogLevel`        | `WARN`           | `outcome`이 `SUCCESS`가 아닌 `onSuccess` 호출(`UNAUTHENTICATED`, `FORBIDDEN`, `CLIENT_ERROR` 등)의 로그 레벨 |
| `telemetry.propagation.mode`                        | `PropagationMode` | `W3C_STANDARD`   | `W3C_STANDARD` 또는 `CUSTOM` |
| `telemetry.propagation.generate-when-missing`       | `Boolean`         | `true`           | 요청 헤더에 ID가 없을 때 자동 생성      |
| `telemetry.propagation.custom-headers.trace-id`     | `String`          | `X-Trace-Id`     | 커스텀 트레이스 ID 헤더명            |
| `telemetry.propagation.custom-headers.causation-id` | `String`          | `X-Causation-Id` | 커스텀 인과관계 ID 헤더명            |

---