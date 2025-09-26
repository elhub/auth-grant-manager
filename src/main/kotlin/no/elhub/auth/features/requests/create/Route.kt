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
import no.elhub.auth.features.requests.get.Handler as GetHandler
import no.elhub.auth.features.requests.get.Query
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.route(createHandler: Handler, getHandler: GetHandler) {
    post {
        val payload = runCatching {
            call.receive<Request>()
        }.getOrElse { exception ->
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@post
        }

        val requestId = createHandler(payload.toCommand())
            .getOrElse { error ->
                when (error) {
                    is
                    no.elhub.auth.features.requests.create.Error.MappingError,
                    Error.PersistenceError
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
