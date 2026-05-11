package io.github.hchanjune.omk.webmvc.async

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.webmvc.Operations
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class ManagedContextElement(
    private val managedContext: ManagedContext
): CopyableThreadContextElement<ManagedContext?> {

    companion object Key : CoroutineContext.Key<ManagedContextElement>
    override val key: CoroutineContext.Key<*>
        get() = Key

    private val hookRegistered = AtomicBoolean(false)

    override fun copyForChild(): CopyableThreadContextElement<ManagedContext?> {
        return ManagedContextElement(managedContext.forkAsync())
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
        return overwritingElement
    }

    override fun updateThreadContext(context: CoroutineContext): ManagedContext? {
        val old = if (Operations.hasContext) Operations.context else null
        Operations.applyContext(managedContext)

        if (managedContext.isAsync && managedContext.isAsyncHookEnabled && hookRegistered.compareAndSet(false, true)) {
            managedContext.start()
            context[Job]?.invokeOnCompletion { cause ->
                val hook = Operations.hook ?: return@invokeOnCompletion
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

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: ManagedContext?
    ) {
        if (oldState == null) Operations.clear()
        else Operations.applyContext(oldState)
    }

}
