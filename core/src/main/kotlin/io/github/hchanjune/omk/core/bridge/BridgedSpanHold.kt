package io.github.hchanjune.omk.core.bridge

/**
 * Holds back the backend end of every span of one execution until its entry point decides whether
 * the execution is worth exporting. A child span ends before its parent knows the outcome, so
 * without holding, a `quietWhenEmpty` poller would already have exported its children by the time
 * it finds there was nothing to do. A span that is never ended is never exported.
 *
 * Shared with async forks of the same execution, which may end after the decision — a late span
 * simply follows it. Thread-safe.
 *
 * 한 실행의 span 들을 진입점이 내보낼지 결정할 때까지 백엔드에서 끝내지 않고 붙들어 둔다. 자식 span 은
 * 부모가 결과를 알기 전에 끝나므로, 붙들지 않으면 `quietWhenEmpty` 폴러는 할 일이 없다는 걸 알았을 때
 * 이미 자식 span 을 내보낸 뒤다. 끝나지 않은 span 은 내보내지지 않는다.
 *
 * 같은 실행의 비동기 fork 와 공유하며, fork 가 결정 뒤에 끝나면 그 결정을 그대로 따른다. 스레드 안전하다.
 */
internal class BridgedSpanHold {

    private val held = ArrayList<() -> Unit>()
    private var export: Boolean? = null

    fun onSpanEnded(endBackendSpan: () -> Unit) {
        val decided = synchronized(this) {
            if (export == null) held.add(endBackendSpan)
            export
        }
        if (decided == true) endBackendSpan()
    }

    fun release() = decide(export = true).forEach { it() }

    fun discard() {
        decide(export = false)
    }

    private fun decide(export: Boolean): List<() -> Unit> = synchronized(this) {
        if (this.export != null) return emptyList()
        this.export = export
        held.toList().also { held.clear() }
    }
}
