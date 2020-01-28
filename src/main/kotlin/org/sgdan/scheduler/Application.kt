package org.sgdan.scheduler

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build()
                .packages("org.sgdan.scheduler")
                .mainClass(Application.javaClass)
                .start()
    }
}
