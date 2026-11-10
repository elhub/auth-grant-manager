package no.elhub.auth.v1.api.grants.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.auth.PartyType
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.api.common.validateEnumListParam
import no.elhub.auth.v1.api.grants.response.toGetCollectionResponse
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.GrantPartyFilters
import no.elhub.auth.v1.features.grants.ListGrantsHandler
import java.util.UUID

internal const val STATUS_FILTER_PARAM = "filter[status]"
internal const val GRANTED_BY_FILTER_PARAM = "filter[grantedBy]"
internal const val PRINCIPAL_FILTER_PARAM = "filter[principal]"
internal const val APPROVED_BY_FILTER_PARAM = "filter[approvedBy]"
internal const val GRANTED_TO_FILTER_PARAM = "filter[grantedTo]"

fun Route.route(handler: ListGrantsHandler) {
    get {
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )

        val statuses = validateEnumListParam<AuthorizationGrantStatus>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM,
        )

        val partyFilters = GrantPartyFilters(
            grantedBy = parsePartyIds(call.request.queryParameters[GRANTED_BY_FILTER_PARAM], GRANTED_BY_FILTER_PARAM),
            principal = parsePartyIds(call.request.queryParameters[PRINCIPAL_FILTER_PARAM], PRINCIPAL_FILTER_PARAM),
            approvedBy = parsePartyIds(call.request.queryParameters[APPROVED_BY_FILTER_PARAM], APPROVED_BY_FILTER_PARAM),
            grantedTo = parsePartyIds(call.request.queryParameters[GRANTED_TO_FILTER_PARAM], GRANTED_TO_FILTER_PARAM),
        )

        val page = handler(call.authorizedParty, pagination, statuses, partyFilters)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses, partyFilters))
    }
}

private fun parsePartyIds(param: String?, paramName: String): List<AuthorizationParty> {
    if (param.isNullOrBlank()) return emptyList()
    return param.split(',').map { id ->
        when {
            id.matches(Regex("[0-9]{9}")) -> AuthorizationParty(id, PartyType.Organization)

            id.matches(Regex("[0-9]{13}")) -> AuthorizationParty(id, PartyType.OrganizationEntity)

            else -> try {
                AuthorizationParty(UUID.fromString(id).toString(), PartyType.Person)
            } catch (_: IllegalArgumentException) {
                throw InputError.InvalidQueryParameterError(
                    "Invalid $paramName value '$param'. Expected comma-separated Person UUIDs, 9-digit organization numbers or 13-digit GLNs."
                )
            }
        }
    }.distinct()
}
