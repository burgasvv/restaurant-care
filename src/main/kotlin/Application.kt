package org.burgas

import io.ktor.server.application.*
import org.burgas.plugin.configureAuthentication
import org.burgas.plugin.configureDatabases
import org.burgas.plugin.configureRouting
import org.burgas.plugin.configureSerialization
import org.burgas.service.configureEmployeeRouter
import org.burgas.service.configureFileRouter
import org.burgas.service.configureIdentityRouter
import org.burgas.service.configureLocationRouter
import org.burgas.service.configureRestaurantRouter

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureDatabases()
    configureRouting()
    configureAuthentication()

    configureFileRouter()
    configureIdentityRouter()
    configureEmployeeRouter()
    configureRestaurantRouter()
    configureLocationRouter()
}
