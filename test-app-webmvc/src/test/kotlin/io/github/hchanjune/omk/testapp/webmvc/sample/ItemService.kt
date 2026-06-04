package io.github.hchanjune.omk.testapp.webmvc.sample

import io.github.hchanjune.omk.core.annotations.ManagedOperation
import io.github.hchanjune.omk.core.annotations.ManagedService
import org.springframework.stereotype.Service

@Service
@ManagedService
class ItemService(
    private val itemRepository: ItemRepository,
    private val itemValidator: ItemValidator
) {

    @ManagedOperation(operation = "GetAllItems", useCase = "SampleManagement")
    fun getAll(): List<SampleItem> = itemRepository.findAll()

    @ManagedOperation(operation = "GetItem", useCase = "SampleManagement")
    fun getById(id: Long): SampleItem =
        itemRepository.findById(id) ?: throw NoSuchElementException("Item $id not found")

    @ManagedOperation(operation = "CreateItem", useCase = "SampleManagement")
    fun create(name: String): SampleItem {
        itemValidator.validate(name)   // @ManagedMetric — tracked as a child span under CreateItem
        return itemRepository.save(name)
    }

    @ManagedOperation(operation = "DeleteItem", useCase = "SampleManagement")
    fun delete(id: Long) {
        if (!itemRepository.deleteById(id)) throw NoSuchElementException("Item $id not found")
    }
}
