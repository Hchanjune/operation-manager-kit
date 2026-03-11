package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.context.ManagedContext

interface OperationHook {

    fun onSuccess(context: ManagedContext) {}

    fun onFailure(context: ManagedContext, exception: Throwable) {}

}