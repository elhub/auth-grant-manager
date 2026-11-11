package no.elhub.auth.v1.api.documents.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v1.api.common.validateEnumListParam
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.documents.response.ResponseAuthorizationDocumentStatus
import no.elhub.auth.v1.api.documents.response.toGetCollectionResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.features.documents.ListDocumentsHandler

private const val STATUS_FILTER_PARAM = "filter[status]"

fun Route.route(handler: ListDocumentsHandler) {
    get {
        call.validateQueryParameters("page[number]", "page[size]", STATUS_FILTER_PARAM)
        val pagination = Pagination.from(
            pageParam = call.request.queryParameters["page[number]"],
            sizeParam = call.request.queryParameters["page[size]"],
        )
        val statuses = validateEnumListParam<ResponseAuthorizationDocumentStatus>(
            call.request.queryParameters[STATUS_FILTER_PARAM],
            STATUS_FILTER_PARAM,
        )
        val domainStatuses = statuses.map { status ->
            when (status) {
                ResponseAuthorizationDocumentStatus.Approved -> AuthorizationDocumentStatus.Approved
                ResponseAuthorizationDocumentStatus.Expired -> AuthorizationDocumentStatus.Expired
                ResponseAuthorizationDocumentStatus.Pending -> AuthorizationDocumentStatus.Pending
            }
        }

        val page = handler(call.authorizedAgent, pagination, domainStatuses)
        call.respond(HttpStatusCode.OK, page.toGetCollectionResponse(statuses))
    }
}
