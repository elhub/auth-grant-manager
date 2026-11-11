package no.elhub.auth.v1.api.grants.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.common.GrantResourceType
import no.elhub.auth.v1.api.common.errors.InputError
import no.elhub.auth.v1.api.common.validateEnumListParam
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.grants.response.toGetCollectionResponse
import no.elhub.auth.v1.common.auth.AgentType
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.domain.AuthorizationGrantStatus
import no.elhub.auth.v1.domain.AuthorizationGrantType
import no.elhub.auth.v1.domain.GrantAgentFilters
import no.elhub.auth.v1.features.grants.ListGrantsHandler
import java.util.UUID

internal const val STATUS_FILTER_PARAM = "filter[status]"
internal const val TYPE_FILTER_PARAM = "filter[type]"
internal const val GRANTED_BY_FILTER_PARAM = "filter[grantedBy]"
internal const val PRINCIPAL_FILTER_PARAM = "filter[principal]"
internal const val APPROVED_BY_FILTER_PARAM = "filter[approvedBy]"
internal const val GRANTED_TO_FILTER_PARAM = "filter[grantedTo]"

fun Route.route(handler: ListGrantsHandler) {
    get {
        call.validateQueryParameters(
            "page[number]",
            "page[size]",
            STATUS_FILTER_PARAM,
            TYPE_FILTER_PARAM,
            GRANTED_BY_FILTER_PARAM,
            PRINCIPAL_FILTER_PARAM,
            APPROVED_BY_FILTER_PARAM,
            GRANTED_TO_FILTER_PARAM,
        )
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )

        val statuses = validateEnumListParam<AuthorizationGrantStatus>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM,
        )
        val types = validateEnumListParam<GrantResourceType>(
            call.request.queryParameters[TYPE_FILTER_PARAM],
            TYPE_FILTER_PARAM,
        ).map { type ->
            when (type) {
                GrantResourceType.PersistentGrant -> AuthorizationGrantType.Persistent
                GrantResourceType.ConsumableGrant -> AuthorizationGrantType.Consumable
            }
        }

        val agentFilters = GrantAgentFilters(
            grantedBy = parseAgentIds(call.request.queryParameters[GRANTED_BY_FILTER_PARAM], GRANTED_BY_FILTER_PARAM),
            principal = parseAgentIds(call.request.queryParameters[PRINCIPAL_FILTER_PARAM], PRINCIPAL_FILTER_PARAM),
            approvedBy = parseAgentIds(call.request.queryParameters[APPROVED_BY_FILTER_PARAM], APPROVED_BY_FILTER_PARAM),
            grantedTo = parseAgentIds(call.request.queryParameters[GRANTED_TO_FILTER_PARAM], GRANTED_TO_FILTER_PARAM),
        )

        val page = handler(call.authorizedAgent, pagination, statuses, agentFilters, types)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses, agentFilters, types))
    }
}

private fun parseAgentIds(param: String?, paramName: String): List<AuthorizationAgent> {
    if (param.isNullOrBlank()) return emptyList()
    return param.split(',').map { id ->
        when {
            id.matches(Regex("[0-9]{9}")) -> AuthorizationAgent(id, AgentType.Organization)

            id.matches(Regex("[0-9]{13}")) -> AuthorizationAgent(id, AgentType.OrganizationEntity)

            else -> try {
                AuthorizationAgent(UUID.fromString(id).toString(), AgentType.Person)
            } catch (_: IllegalArgumentException) {
                throw InputError.InvalidQueryParameterError(
                    "Invalid $paramName value '$param'. Expected comma-separated Person UUIDs, 9-digit organization numbers or 13-digit GLNs."
                )
            }
        }
    }.distinct()
}
