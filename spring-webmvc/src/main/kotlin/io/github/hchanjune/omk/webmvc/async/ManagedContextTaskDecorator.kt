package io.github.hchanjune.omk.webmvc.async

import io.github.hchanjune.omk.webmvc.Operations
import org.springframework.core.task.TaskDecorator

class ManagedContextTaskDecorator: TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        if(!Operations.hasContext) return runnable
        val context = Operations.context
        return Runnable {
            Operations.applyContext(context)
            try {
                runnable.run()
            } finally {
                Operations.clear()
            }
        }
    }
}