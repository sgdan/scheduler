package org.sgdan.scheduler

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlinx.coroutines.runBlocking

@Controller("/")
class Controller(private val backend: Backend) {

    @Get("/scheduler/status")
    fun status(): Status = runBlocking { backend.getStatus() }

    @Get("/scheduler/extend")
    fun extend() = runBlocking { backend.extend() }
}
