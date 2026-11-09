package io.github.hchanjune.omk.core.metric

import io.github.hchanjune.omk.core.bridge.BridgedSpan
import io.github.hchanjune.omk.core.bridge.BridgedSpanHold
import io.github.hchanjune.omk.core.bridge.SpanBridge
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

class MetricSpan(
    val traceId: String,
    val spanId: String,
    val name: MetricName,
    val kind: MetricKind,
    val policy: MetricPolicy,
    val tags: MetricTags,
    val descriptor: MetricDescriptor,
    private val clock: Clock = Clock.systemUTC()
) {
    val threadName: String = Thread.currentThread().name
    private val timing: MetricTiming = MetricTiming.started(clock)

    var parent: MetricSpan? = null
        private set

    private val _children = mutableListOf<MetricSpan>()
    private val _childSpanIds = HashSet<String>()
    val children: List<MetricSpan> get() = _children

    val startTime: Long? get() = timing.startedAtEpochMilli

    val durationMs: Long? get() = timing.durationMillis()

    var outcome: MetricOutcome? = null
        private set

    /**
     * Cache hit flag for `[CAC]` layer spans; `null` on every other layer and before the lookup
     * has been decided. Set via [markCacheHit].
     *
     * Hit/miss is a **separate axis from success/failure** — a Redis timeout is a FAILURE, not a
     * MISS, and both facts may need to be reported at once. Keeping it out of [MetricOutcome] is
     * deliberate: folding it in would force every consumer (metrics, alerting, the OTel bridge) to
     * re-learn that "MISS is not an error", and missing that in any one of them turns cache misses
     * into an error rate.
     *
     * 캐시(`[CAC]`) 스팬의 히트 여부. 다른 레이어이거나 아직 판정 전이면 null 이다([markCacheHit] 로 설정).
     *
     * 히트/미스는 **성공/실패와 독립적인 축**이다 — Redis 타임아웃은 MISS 가 아니라 FAILURE 이고,
     * 두 사실을 동시에 알려야 할 수도 있다. [MetricOutcome] 에 섞지 않은 것은 의도적이다.
     * 섞으면 메트릭·알림·OTel 브릿지가 각각 "MISS 는 실패가 아니다"를 알아야 하고,
     * 하나만 빠뜨려도 캐시 미스가 에러율로 잡힌다.
     */
    var cacheHit: Boolean? = null
        private set

    /**
     * Records whether the cache lookup was served from cache. Call it from the cache aspect once
     * the lookup is decided — the span itself is opened before that is known.
     *
     * 캐시 조회가 캐시에서 제공됐는지 기록한다. 스팬은 판정 전에 열리므로,
     * 캐시 애스펙트가 조회 직후에 호출한다.
     */
    fun markCacheHit(hit: Boolean) {
        this.cacheHit = hit
    }

    // Live backend span this OMK span is bridged onto; set by ManagedContext.push.
    internal var bridge: SpanBridge? = null
    internal var bridgeHandle: BridgedSpan? = null
    internal var bridgeHold: BridgedSpanHold? = null

    /**
     * The backend-native context of the bridged live span (e.g. an OTel Context), or null when
     * no bridge is active. Stack adapters use it to propagate the span as the backend's current
     * context (reactive: written into the Reactor context so instrumented clients nest under it).
     */
    val bridgedContext: Any? get() = bridgeHandle?.nativeContext

    fun addChild(child: MetricSpan) {
        check(child.parent == null) {
            "Span [${child.spanId}] already has a parent [${child.parent?.spanId}]"
        }
        if (!_childSpanIds.add(child.spanId)) return
        child.parent = this
        _children.add(child)
    }

    fun end() = finish(MetricOutcome.success())
    fun end(exception: Throwable) = finish(MetricOutcome.fail(exception))
    fun end(outcome: MetricOutcome) = finish(outcome)

    private fun finish(outcome: MetricOutcome) {
        this.timing.end(clock)
        this.outcome = outcome
        val handle = bridgeHandle ?: return
        val hold = bridgeHold
        bridgeFailOpen {
            if (hold == null) {
                bridge?.endSpan(handle, this)
            } else {
                bridge?.releaseScope(handle)
                val endTime = Instant.now(clock)
                hold.onSpanEnded { bridgeFailOpen { bridge?.endSpan(handle, this, endTime) } }
            }
        }
    }

    private inline fun bridgeFailOpen(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            log.warn("Span bridge failed to end span [{}]: {}", spanId, e.toString())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetricSpan::class.java)
    }
}