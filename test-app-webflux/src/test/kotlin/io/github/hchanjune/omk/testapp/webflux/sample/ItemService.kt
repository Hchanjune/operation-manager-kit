package io.github.hchanjune.omk.testapp.webflux.sample

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.annotations.ManagedService
import io.github.hchanjune.omk.webflux.ReactiveOperations
import org.springframework.stereotype.Service

@Service
@ManagedService
class ItemService(
    private val itemRepository: ItemRepository,
    private val itemValidator: ItemValidator
) {

    @ManagedOperation(operation = "GetAllItems", useCase = "SampleManagement")
    suspend fun getAll() = ReactiveOperations { itemRepository.findAll() }

    @ManagedOperation(operation = "GetItem", useCase = "SampleManagement")
    suspend fun getById(id: Long) = ReactiveOperations {
        itemRepository.findById(id) ?: throw NoSuchElementException("Item $id not found")
    }

    @ManagedOperation(operation = "CreateItem", useCase = "SampleManagement")
    suspend fun create(name: String) = ReactiveOperations {
        itemValidator.validate(name)
        itemRepository.save(name)
    }

    @ManagedOperation(operation = "DeleteItem", useCase = "SampleManagement")
    suspend fun delete(id: Long) = ReactiveOperations {
        if (!itemRepository.deleteById(id)) throw NoSuchElementException("Item $id not found")
    }
}
