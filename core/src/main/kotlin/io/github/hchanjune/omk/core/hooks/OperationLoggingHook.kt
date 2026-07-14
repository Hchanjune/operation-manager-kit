package io.github.hchanjune.omk.core.hooks

import io.github.hchanjune.omk.core.OperationHook

/**
 * Marker for the hook responsible for operation logging. Providing a custom bean of this
 * type replaces the default logging hook (via @ConditionalOnMissingBean in the stack modules).
 */
interface OperationLoggingHook : OperationHook
