package no.elhub.auth.v1.api.documents.routes

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.common.auth.authorizedParty
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.toApiErrorResponse
import no.elhub.auth.v0.features.common.validateEnumListParam
import no.elhub.auth.v1.api.documents.response.ResponseAuthorizationDocumentStatus
import no.elhub.auth.v1.api.documents.response.toGetCollectionResponse
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.features.documents.ListDocumentsHandler

private const val STATUS_FILTER_PARAM = "filter[status]"

fun Route.route(handler: ListDocumentsHandler) {
    get {
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )
        val statuses = validateEnumListParam<ResponseAuthorizationDocumentStatus>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM,
        ).getOrElse { error ->
            val (status, body) = error.toApiErrorResponse()
            call.respond(status, body)
            return@get
        }
        val domainStatuses = statuses.map { status ->
            when (status) {
                ResponseAuthorizationDocumentStatus.Approved -> AuthorizationDocumentStatus.Approved
                ResponseAuthorizationDocumentStatus.Expired -> AuthorizationDocumentStatus.Expired
                ResponseAuthorizationDocumentStatus.Pending -> AuthorizationDocumentStatus.Pending
            }
        }

        val page = handler(call.authorizedParty, pagination, domainStatuses)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses))
    }
}
