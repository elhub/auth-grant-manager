package no.elhub.auth.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
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
