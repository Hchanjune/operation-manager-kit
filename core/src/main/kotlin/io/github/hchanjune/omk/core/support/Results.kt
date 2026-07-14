package io.github.hchanjune.omk.core.support

/**
 * Shared "empty result" judgement used by @ManagedSchedule(quietWhenEmpty) in both stacks:
 * null, Unit, 0, false, and empty Collection/Map/Array/CharSequence count as empty.
 */
object Results {

    fun isEmpty(result: Any?): Boolean = when (result) {
        null, Unit -> true
        is Number -> result.toLong() == 0L
        is Boolean -> !result
        is Collection<*> -> result.isEmpty()
        is Map<*, *> -> result.isEmpty()
        is Array<*> -> result.isEmpty()
        is CharSequence -> result.isEmpty()
        else -> false
    }
}
