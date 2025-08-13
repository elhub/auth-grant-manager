package no.elhub.auth.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import no.elhub.auth.features.errors.UnexpectedError
import no.elhub.auth.features.errors.mapErrorToResponse
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Exception> { call, e ->
            val response = mapErrorToResponse(UnexpectedError.UnexpectedFailure(e))
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = JsonApiErrorCollection(listOf(response))
            )
        }
    }
}
