package no.elhub.auth.features.requests.query

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.features.common.InputError
import no.elhub.auth.features.common.Pagination
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.toApiErrorResponse
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.query.dto.toGetCollectionResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(handler: Handler, authProvider: AuthorizationProvider) {
    get {
        val authorizedParty = authProvider.authorizeEndUserOrMaskinporten(call)
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )

        val status = validateStatusParam(call.request.queryParameters["filter[status]"])
            .getOrElse { err ->
                val (status, body) = err.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }

        val query = Query(authorizedParty = authorizedParty, pagination = pagination, status = status)

        val page = handler(query)
            .getOrElse { error ->
                logger.error("Failed to get authorization requests: {}", error)
                val (status, body) = error.toApiErrorResponse()
                call.respond(status, body)
                return@get
            }
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse())
    }
}

private fun validateStatusParam(status: String?): Either<InputError.MalformedInputError, List<AuthorizationRequest.Status>> =
    Either.catch {
        if (status.isNullOrBlank()) {
            return emptyList<AuthorizationRequest.Status>().right()
        }
        status.split(',').map {
            AuthorizationRequest.Status.valueOf(it)
        }
    }.mapLeft {
        InputError.MalformedInputError("Invalid status value '$status'. Valid values: ${AuthorizationRequest.Status.entries.joinToString()}")
    }
