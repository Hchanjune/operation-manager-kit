package io.github.hchanjune.omk.servlet.async

import io.github.hchanjune.omk.servlet.Operations
import org.springframework.core.task.TaskDecorator

class ManagedContextTaskDecorator: TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        if (!Operations.hasContext) return runnable
        val childContext = Operations.context.forkAsync()
        return Runnable {
            Operations.applyContext(childContext)
            childContext.start()
            try {
                runnable.run()
                val hook = Operations.hook
                if (hook != null && childContext.isAsyncHookEnabled) {
                    childContext.rootSpan?.end()
                    childContext.pop()
                    Operations.complete()
                    hook.onSuccess(childContext)
                }
            } catch (e: Throwable) {
                val hook = Operations.hook
                if (hook != null && childContext.isAsyncHookEnabled) {
                    childContext.rootSpan?.end(e)
                    childContext.pop()
                    Operations.complete()
                    hook.onFailure(childContext, e)
                }
                throw e
            } finally {
                Operations.clear()
            }
        }
    }
}
