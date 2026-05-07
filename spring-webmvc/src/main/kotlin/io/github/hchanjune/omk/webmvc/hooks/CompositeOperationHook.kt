package io.github.hchanjune.omk.webmvc.hooks

import io.github.hchanjune.omk.core.OperationHook
import io.github.hchanjune.omk.core.context.ManagedContext
import org.slf4j.LoggerFactory

class CompositeOperationHook(
    private val hooks: List<OperationHook>
): OperationHook {

    private val log = LoggerFactory.getLogger("OperationManager")

    override fun onSuccess(context: ManagedContext) {
        hooks.forEach { hook ->
            val name = hook::class.simpleName ?: "UnknownHook"
            try {
                hook.onSuccess(context)
                context.recordHookSuccess(name)
            } catch (e: Throwable) {
                context.recordHookFailure(name, e)
                log.warn("OperationHook [$name] threw on onSuccess", e)
            }
        }
        summarizeFailures(context)
    }

    override fun onFailure(context: ManagedContext, exception: Throwable) {
        hooks.forEach { hook ->
            val name = hook::class.simpleName ?: "UnknownHook"
            try {
                hook.onFailure(context, exception)
                context.recordHookSuccess(name)
            } catch (e: Throwable) {
                context.recordHookFailure(name, e)
                log.warn("OperationHook [$name] threw on onFailure", e)
            }
        }
        summarizeFailures(context)
    }

    private fun summarizeFailures(context: ManagedContext) {
        val failures = context.hookRecords.filter { !it.success }
        if (failures.isNotEmpty()) {
            log.warn("Hook execution completed with ${failures.size} failure(s): ${failures.map { it.hookName }}")
        }
    }

}