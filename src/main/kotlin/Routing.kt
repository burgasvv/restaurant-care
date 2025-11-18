package org.burgas

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureRouting() {

//    install(StatusPages) {
//        exception<Throwable> { call, cause ->
//            call.respond(HttpStatusCode.BadRequest, cause.localizedMessage)
//        }
//    }
}
