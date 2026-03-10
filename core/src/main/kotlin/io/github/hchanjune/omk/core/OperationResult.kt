package io.github.hchanjune.omk.core

import io.github.hchanjune.omk.core.context.ManagedContext

data class OperationResult<T>(
    val context: ManagedContext,
    val data: T
)