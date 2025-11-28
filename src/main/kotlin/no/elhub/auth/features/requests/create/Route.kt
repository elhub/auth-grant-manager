package no.elhub.auth.features.requests.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.features.requests.create.dto.toCreateResponse
import no.elhub.auth.features.requests.create.dto.toModel
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.route(handler: Handler) {
    post {
        val requestBody =
            runCatching {
                call.receive<JsonApiCreateRequest>()
            }.getOrElse {
                val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@post
            }

        val request =
            handler(requestBody.toModel()).getOrElse { error ->
                when (error) {
                    is
                    CreateRequestError.MappingError,
                    CreateRequestError.PersistenceError,
                    CreateRequestError.RequestedByPartyError,
                    CreateRequestError.RequestedFromPartyError,
                    -> call.respond(HttpStatusCode.InternalServerError)
                    is CreateRequestError.ValidationError -> {
                        val (status, error) = error.toApiErrorResponse()
                        call.respond(status, error)
                    }
                }
                return@post
            }

        call.respond(HttpStatusCode.Created, request.toCreateResponse())
    }
}
