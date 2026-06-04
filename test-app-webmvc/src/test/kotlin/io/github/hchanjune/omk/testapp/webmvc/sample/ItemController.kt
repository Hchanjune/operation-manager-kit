package io.github.hchanjune.omk.testapp.webmvc.sample

import io.github.hchanjune.omk.core.annotations.ManagedController
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@ManagedController
@RequestMapping("/sample/items")
class ItemController(private val itemService: ItemService) {

    @GetMapping
    fun getAll(): List<SampleItem> = itemService.getAll()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): SampleItem = itemService.getById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestParam name: String): SampleItem = itemService.create(name)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = itemService.delete(id)
}
