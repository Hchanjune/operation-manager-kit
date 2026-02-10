package io.github.hchanjune.operationresult.core

import io.github.hchanjune.operationresult.core.defaults.DefaultOperationExecutorFactory
import io.github.hchanjune.operationresult.core.models.OperationContext

/**
 * Global entrypoint for executing operations.
 *
 * ## Purpose
 * [Operations] provides a simple, static-like API to run business logic within
 * an operation boundary, producing consistent metadata and [OperationResult].
 *
 * Instead of injecting [OperationExecutor] everywhere, applications can use:
 *
 * ```kotlin
 * val result = Operations {
 *     // business logic
 * }
 *
 * or
 *
 * fun handle(event: Event) = Operations {
 *     // business logic
 * }
 * ```
 *
 * ## Executor configuration
 * Internally, [Operations] delegates execution to a single [OperationExecutor].
 *
 * By default, a minimal executor is created via [DefaultOperationExecutorFactory].
 * Framework integrations (e.g. Spring Boot auto-configuration) are expected to
 * call [configure] once during application startup to install the appropriate executor.
 *
 * ## Thread-safety
 * The executor reference is stored as a volatile variable to ensure visibility
 * across threads after configuration.
 *
 * ## Notes
 * - [configure] should typically be called only once at startup.
 * - If multiple configurations occur, the latest executor replaces the previous one.
 */
object Operations {
    /**
     * The currently configured executor used to run operations.
     *
     * Initialized with a default executor and may be replaced via [configure].
     */
    @Volatile private var executor: OperationExecutor = DefaultOperationExecutorFactory.create()

    /**
     * Configures the global [OperationExecutor].
     *
     * This is typically invoked by framework auto-configuration during application startup.
     *
     * @param executor the executor instance to use for subsequent operations
     */
    fun configure(executor: OperationExecutor) {
        this.executor = executor
    }

    /**
     * Executes the given [block] as an operation using the currently configured executor.
     *
     * @param block operation logic to execute
     * @return an [OperationResult] containing execution context and produced data
     */
    operator fun <T> invoke(block: OperationContext.() -> T) =
        executor.run(block)
}
