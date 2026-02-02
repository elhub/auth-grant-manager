package no.elhub.auth.features.requests.create

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.elhub.auth.features.common.auth.AuthorizationProvider
import no.elhub.auth.features.common.auth.RoleType
import no.elhub.auth.features.common.auth.toApiErrorResponse
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.common.party.PartyType
import no.elhub.auth.features.common.toBalanceSupplierNotApiAuthorizedResponse
import no.elhub.auth.features.requests.create.dto.JsonApiCreateRequest
import no.elhub.auth.features.requests.create.dto.toCreateResponse
import no.elhub.auth.features.requests.create.dto.toModel
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Route::class.java)

fun Route.route(
    handler: Handler,
    authProvider: AuthorizationProvider,
) {
    post {
        val resolvedActor =
            authProvider
                .authorizeMaskinporten(call)
                .getOrElse {
                    logger.error("Failed to authorize Maskinporten token for POST /authorization-requests: {}", it)
                    val error = it.toApiErrorResponse()
                    call.respond(error.first, error.second)
                    return@post
                }

        val requestBody = call.receive<JsonApiCreateRequest>()
        if (resolvedActor.role != RoleType.BalanceSupplier) {
            val (status, body) = toBalanceSupplierNotApiAuthorizedResponse()
            call.respond(status, body)
            return@post
        }

        val authorizedParty =
            AuthorizationParty(
                resourceId = resolvedActor.gln,
                type = PartyType.OrganizationEntity,
            )

        val request =
            handler(requestBody.toModel(authorizedParty))
                .getOrElse { error ->
                    logger.error("Failed to create authorization request: {}", error)
                    val (status, error) = error.toApiErrorResponse()
                    call.respond(status, error)
                    return@post
                }

        call.respond(
            status = HttpStatusCode.Created,
            message = request.toCreateResponse(),
        )
    }
}
