package org.burgas

import io.ktor.server.application.*
import org.burgas.service.configureFileRouter
import org.burgas.service.configureIdentityRouter

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureDatabases()
    configureRouting()

    configureFileRouter()
    configureIdentityRouter()
}
