package io.github.hchanjune.operationresult.core.defaults

import io.github.hchanjune.operationresult.core.models.OperationContext
import io.github.hchanjune.operationresult.core.providers.OperationListener

class CompositeOperationListener(
    private val delegates: List<OperationListener>
): OperationListener {

    override fun onSuccess(context: OperationContext) {
        forEachSafely { it.onSuccess(context) }
    }

    override fun onFailure(context: OperationContext, exception: Throwable) {
        forEachSafely { it.onFailure(context, exception) }
    }

    private inline fun forEachSafely(block: (OperationListener) -> Unit) {
        for (delegate in delegates) {
            runCatching { block(delegate) }
                .onFailure { it.printStackTrace() }
        }
    }

}