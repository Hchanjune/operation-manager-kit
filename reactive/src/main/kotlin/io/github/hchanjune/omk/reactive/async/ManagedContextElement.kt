package io.github.hchanjune.omk.reactive.async

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.reactive.ReactiveOperations
import io.github.hchanjune.omk.reactive.aspect.ReactiveAspectSupport
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Carries a [ManagedContext] into a coroutine that leaves the managed execution's thread —
 * `withContext(Dispatchers.IO)`, `launch`, `async`.
 *
 * Every coroutine that receives this element gets its **own forked context** ([copyForChild] →
 * [ManagedContext.forkAsync]), not the parent's: traceId, causationId and entrypoint are inherited
 * and the backend trace continues under the forking span, while the span stack, timing and hook
 * records are the child's own. Sharing one context would mean two threads pushing and popping the
 * same span stack, which produces tangled nesting rather than a missing span.
 *
 * The forked context is published two ways so both shapes of instrumented target find it:
 * the element itself (read from the coroutine context by suspend targets) and a thread-bound
 * slot (read by plain blocking targets, which have no continuation to inspect).
 *
 * Prefer the [managedContextElement] helper at call sites — it resolves the current context for you:
 * ```kotlin
 * withContext(Dispatchers.IO + managedContextElement()) {
 *     userClient.get(accountId)   // @ManagedExternalCall span is recorded here
 * }
 * ```
 *
 * The child's spans are reported through the hook when the async hook is enabled
 * (`operation-manager.reactive.async-propagation.hook-enabled`, on by default) — they surface as a
 * separate trace entry sharing the parent's traceId, not as a branch of the parent's span tree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManagedContextElement(
    val managedContext: ManagedContext
) : CopyableThreadContextElement<ManagedContext?> {

    companion object Key : CoroutineContext.Key<ManagedContextElement>

    override val key: CoroutineContext.Key<*>
        get() = Key

    private val hookRegistered = AtomicBoolean(false)

    override fun copyForChild(): CopyableThreadContextElement<ManagedContext?> =
        ManagedContextElement(managedContext.forkAsync())

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext =
        overwritingElement

    override fun updateThreadContext(context: CoroutineContext): ManagedContext? {
        val old = ReactiveAspectSupport.threadBoundContext.get()
        ReactiveAspectSupport.threadBoundContext.set(managedContext)

        if (managedContext.isAsync && managedContext.isAsyncHookEnabled && hookRegistered.compareAndSet(false, true)) {
            managedContext.start()
            context[Job]?.invokeOnCompletion { cause ->
                val hook = ReactiveOperations.hookFor(managedContext) ?: return@invokeOnCompletion
                if (cause == null) {
                    managedContext.rootSpan?.end()
                    managedContext.pop()
                    managedContext.end()
                    hook.onSuccess(managedContext)
                } else {
                    managedContext.rootSpan?.end(cause)
                    managedContext.pop()
                    managedContext.end()
                    hook.onFailure(managedContext, cause)
                }
            }
        }

        return old
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: ManagedContext?) {
        if (oldState == null) ReactiveAspectSupport.threadBoundContext.remove()
        else ReactiveAspectSupport.threadBoundContext.set(oldState)
    }
}

/**
 * The [ManagedContextElement] for the context governing the current execution, or
 * [EmptyCoroutineContext] when there is none — so the call site never has to unwrap a null:
 *
 * ```kotlin
 * withContext(Dispatchers.IO + managedContextElement()) { ... }
 * ```
 *
 * Returning empty rather than opening a detached context is deliberate: an unmanaged scope stays
 * unmanaged instead of emitting a stray trace, and the aspects inside already warn when they run
 * without a context.
 */
suspend fun managedContextElement(): CoroutineContext =
    ReactiveOperations.currentContextOrNull()?.let { ManagedContextElement(it) } ?: EmptyCoroutineContext
