package io.github.hchanjune.omk.testapp.servlet.sample

import io.github.hchanjune.omk.core.annotations.ManagedRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
@ManagedRepository
class ItemRepository {

    private val store = ConcurrentHashMap<Long, SampleItem>()
    private val idSeq = AtomicLong(1)

    fun findAll(): List<SampleItem> = store.values.toList()

    fun findById(id: Long): SampleItem? = store[id]

    fun save(name: String): SampleItem {
        val item = SampleItem(id = idSeq.getAndIncrement(), name = name)
        store[item.id] = item
        return item
    }

    fun deleteById(id: Long): Boolean = store.remove(id) != null
}
