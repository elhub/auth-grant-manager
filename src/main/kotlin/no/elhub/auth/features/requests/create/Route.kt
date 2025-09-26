package no.elhub.auth.features.requests.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.requests.common.toResponse
import no.elhub.auth.features.requests.get.Handler
import no.elhub.auth.features.requests.get.Query
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.createRequestRoute(createHandler: CreateRequestHandler, getHandler: Handler) {
    post {
        val payload = runCatching {
            call.receive<CreateRequestRequest>()
        }.getOrElse { exception ->
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@post
        }

        val requestId = createHandler(payload.toCreateRequestCommand())
            .getOrElse { error ->
                when (error) {
                    is
                    CreateRequestError.MappingError,
                    CreateRequestError.PersistenceError
                        -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        val authorizationRequest = getHandler(Query(requestId))
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, JsonApiErrorCollection(listOf(body)))
                return@post
            }

        call.respond(HttpStatusCode.Created, authorizationRequest.toResponse())
    }
}
