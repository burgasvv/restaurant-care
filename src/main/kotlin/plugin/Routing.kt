package org.burgas.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import java.util.*

fun Application.configureRouting() {

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.localizedMessage)
        }
    }

    install(CORS) {
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowSameOrigin = true
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowOrigins { string -> string.equals("http://localhost:4200", false) }
        allowHost("localhost")
    }

    install(CSRF) {
        allowOrigin("http://localhost:9000")
        originMatchesHost()
        checkHeader("X-CSRF-Token")
    }

    install(Sessions) {
        cookie<CsrfToken>("MY_SESSION")
    }

    routing {

        route("/api/v1") {

            get("/security/csrf-token") {
                val csrfToken = UUID.randomUUID().toString()
                call.sessions.set(CsrfToken(csrfToken))
                call.respondText("Csrf token: $csrfToken")
            }
        }
    }
}

@Serializable
data class CsrfToken(val token: String)