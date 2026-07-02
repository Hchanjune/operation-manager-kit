package io.github.hchanjune.omk.testapp.reactive.sample

import io.github.hchanjune.omk.core.annotations.ManagedMetric
import org.springframework.stereotype.Component

@Component
class ItemValidator {

    @ManagedMetric("item.validate")
    suspend fun validate(name: String) {
        require(name.isNotBlank()) { "Name must not be blank" }
        require(name.length <= 50) { "Name must not exceed 50 characters" }
    }
}
