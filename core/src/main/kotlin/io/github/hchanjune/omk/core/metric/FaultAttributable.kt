package io.github.hchanjune.omk.core.metric

/**
 * An exception that knows who is at fault for it. [MetricOutcome.fail] uses this to decide between
 * [MetricStatus.FAILURE_CLIENT] and [MetricStatus.FAILURE_SERVER] instead of guessing from the exception type.
 * 자기 귀책을 아는 예외. [MetricOutcome.fail] 이 예외 타입으로 추측하지 않고 이 값으로
 * [MetricStatus.FAILURE_CLIENT] / [MetricStatus.FAILURE_SERVER] 를 가른다.
 *
 * Implement it on your application's base exception — e.g. a policy exception that carries an HTTP-like status:
 * 애플리케이션의 기반 예외에 구현한다 — 예: HTTP 와 비슷한 status 를 가진 정책 예외:
 *
 * ```kotlin
 * abstract class PolicyException(val status: Int) : RuntimeException(), FaultAttributable {
 *     override val isClientFault: Boolean get() = status in 400..499
 * }
 * ```
 *
 * Without it, every exception other than `IllegalArgumentException`/`IllegalStateException` counts as a server
 * failure — so a "not found" or "insufficient balance" rejection would raise the server error rate.
 * 구현하지 않으면 `IllegalArgumentException`/`IllegalStateException` 외의 예외는 전부 서버 실패로 집계된다 —
 * "없음"·"잔액 부족" 같은 정상적인 거절이 서버 에러율을 올린다.
 *
 * The contract is about fault, not HTTP: OMK also runs on non-HTTP entry points (events, schedules), so it never
 * interprets status codes itself.
 * 계약은 HTTP 가 아니라 귀책이다 — OMK 는 HTTP 가 아닌 진입점(이벤트·스케줄)에서도 돌기 때문에 status 코드를 직접 해석하지 않는다.
 */
interface FaultAttributable {

    /**
     * `true` when the caller caused the failure (bad input, rule violation, missing resource), `false` when the system did.
     * 호출한 쪽이 원인이면(잘못된 입력·규칙 위반·없는 자원) `true`, 시스템이 원인이면 `false`.
     */
    val isClientFault: Boolean
}
