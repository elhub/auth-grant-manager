package no.elhub.auth.features.requests.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.devxp.jsonapi.response.JsonApiErrorCollection

fun Route.route(handler: Handler) {
    post {
        val requestBody = runCatching {
            call.receive<CreateRequest>()
        }.getOrElse {
            val (status, body) = InputError.MalformedInputError.toApiErrorResponse()
            call.respond(status, JsonApiErrorCollection(listOf(body)))
            return@post
        }

        val requestType = requestBody.data.attributes.requestType
        val command = when (requestType) {
            AuthorizationRequest.Type.ChangeOfSupplierConfirmation -> requestBody.toChangeOfSupplierRequestCommand()
        }.getOrElse {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }

        val request = handler(command)
            .getOrElse { error ->
                when (error) {
                    is
                    CreateRequestError.MappingError,
                    CreateRequestError.PersistenceError,
                    CreateRequestError.RequestedByPartyError,
                    CreateRequestError.RequestedFromPartyError
                    -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        val req = request.toCreateResponse()

        call.respond(HttpStatusCode.Created, request.toCreateResponse())
    }
}
