package no.elhub.auth.v1.api.documents.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.elhub.auth.v1.api.common.PAGE_NUMBER_PARAM
import no.elhub.auth.v1.api.common.PAGE_SIZE_PARAM
import no.elhub.auth.v1.api.common.STATUS_FILTER_PARAM
import no.elhub.auth.v1.api.common.parsePagination
import no.elhub.auth.v1.api.common.validateEnumListParam
import no.elhub.auth.v1.api.common.validateQueryParameters
import no.elhub.auth.v1.api.documents.response.ResponseAuthorizationDocumentStatus
import no.elhub.auth.v1.api.documents.response.toGetCollectionResponse
import no.elhub.auth.v1.common.auth.authorizedAgent
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.features.documents.ListDocumentsHandler

fun Route.route(handler: ListDocumentsHandler) {
    get {
        call.validateQueryParameters(PAGE_NUMBER_PARAM, PAGE_SIZE_PARAM, STATUS_FILTER_PARAM)
        val pagination = call.parsePagination()
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
