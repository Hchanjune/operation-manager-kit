package io.github.hchanjune.omk.webmvc.async

import io.github.hchanjune.omk.core.context.ManagedContext
import io.github.hchanjune.omk.webmvc.Operations
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext

class ManagedContextElement(
    private val managedContext: ManagedContext
): ThreadContextElement<ManagedContext?> {

    companion object Key : CoroutineContext.Key<ManagedContextElement>
    override val key: CoroutineContext.Key<*>
        get() = Key

    override fun updateThreadContext(context: CoroutineContext): ManagedContext? {
        val old = if (Operations.hasContext) Operations.context else null
        Operations.applyContext(managedContext)
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