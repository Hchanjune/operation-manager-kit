package io.github.hchanjune.omk.core.hooks

/**
 * Settings consumed by [DefaultOperationLoggingHook]. Each stack module binds its own
 * @ConfigurationProperties class (operation-manager.servlet.logging / operation-manager.reactive.logging)
 * to this interface, so the yml property surface stays per-stack while the hook implementation
 * is shared.
 */
interface OperationLoggingSettings {
    val enabled: Boolean
    val pretty: Boolean
    val json: Boolean
    val spans: Boolean
    val response: Boolean
    val ip: Boolean
    val successLevel: LogLevel
    val failureLevel: LogLevel
    val clientErrorLevel: LogLevel
}
