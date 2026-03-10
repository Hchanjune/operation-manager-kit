package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.context.ManagedContext

interface OperationListener {

    fun onSuccess(context: ManagedContext) {}

    fun onFailure(context: ManagedContext, exception: Throwable) {}

}