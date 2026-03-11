package io.github.hchanjune.omk.webmvc.hooks

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext

class CompositeOperationHook(
    private val hooks: List<OperationHook>
): OperationHook {

    override fun onSuccess(context: ManagedContext) {
        hooks.forEach { it.onSuccess(context) }
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        hooks.forEach { it.onFailure(context, exception) }
    }

}