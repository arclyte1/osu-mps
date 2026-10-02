package osu.mps.app.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureHTTP() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to "internal_server_error",
                    "message" to (cause.message ?: "Unexpected error")
                )
            )
        }
    }
}
