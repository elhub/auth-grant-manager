package no.elhub.auth.features.requests.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.util.url
import no.elhub.auth.features.common.QueryError
import no.elhub.auth.features.requests.common.toResponse
import no.elhub.auth.features.requests.get.GetRequestHandler
import no.elhub.auth.features.requests.get.GetRequestQuery

fun Route.createRequestRoute(createHandler: CreateRequestHandler, getHandler: GetRequestHandler) {
    post {
        val requestBody = call.receive<HttpRequestBody>()

        val requestId = createHandler(requestBody.toCreateRequestCommand())
            .getOrElse { error ->
                when (error) {
                    is
                    CreateRequestError.MappingError,
                    CreateRequestError.PersistenceError
                        -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        val authorizationRequest = getHandler(GetRequestQuery(requestId))
            .getOrElse { error ->
                when (error) {
                    is QueryError.ResourceNotFoundError -> call.respond(HttpStatusCode.NotFound)
                    is QueryError.IOError -> call.respond(HttpStatusCode.InternalServerError)
                }
                return@post
            }

        call.respond(HttpStatusCode.Created, authorizationRequest.toResponse())
    }
}
