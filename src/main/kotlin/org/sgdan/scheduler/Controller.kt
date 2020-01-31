package org.sgdan.scheduler

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlinx.coroutines.runBlocking

@Controller("/")
class Controller(private val backend: Backend) {

    @Get("/scheduler/status")
    fun status(): HttpResponse<Status> = runBlocking {
        HttpResponse.ok(backend.getStatus())
                .header("Cache-Control", "no-cache")
    }

    @Get("/scheduler/extend")
    fun extend(): HttpResponse<Status> = runBlocking {
        HttpResponse.ok(backend.extend())
                .header("Cache-Control", "no-cache")
    }
}
