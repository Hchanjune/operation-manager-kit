package io.github.hchanjune.omk.core.annotations

/**
 * Span layer: `[ENT]` ENTRY — opens a **root** span for a scheduler-triggered method and sets
 * `executionScope` to `SCHEDULED`. There is no inbound request or message to carry a trace
 * context, so a fresh `traceId`/`causationId` pair is generated for each run.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ManagedSchedule(
    val description: String = "",
    /**
     * When true, the default success log is silenced (defaultLogging = false) if the method
     * returns an "empty" result: null, Unit, 0, false, or an empty Collection/Map/Array/CharSequence.
     * Useful for high-frequency pollers that mostly find nothing to do — return the processed
     * count (or the processed batch) so only meaningful runs are logged.
     * The execution's spans are not exported to the trace backend either, children included
     * (a poller would otherwise flood it with traces of nothing).
     * Failures are always logged and exported regardless. Metrics are recorded either way.
     *
     * true 면 메서드가 "빈" 결과를 반환했을 때 기본 성공 로그를 끄고, 이 실행의 span 도 자식까지 모두
     * trace 백엔드로 내보내지 않는다(폴러가 할 일 없는 trace 로 백엔드를 채우지 않도록).
     * 실패는 항상 로그를 남기고 내보낸다. 메트릭은 어느 쪽이든 기록한다.
     */
    val quietWhenEmpty: Boolean = false
)
