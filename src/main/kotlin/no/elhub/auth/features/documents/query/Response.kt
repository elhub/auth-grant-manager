package no.elhub.auth.features.documents.query

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.DOCUMENTS_PATH
import no.elhub.auth.features.documents.get.GetDocumentResponseAttributes
import no.elhub.auth.features.documents.get.GetDocumentResponseRelationships
import no.elhub.auth.features.documents.get.toGetResponse
import no.elhub.devxp.jsonapi.model.JsonApiLinks
import no.elhub.devxp.jsonapi.response.JsonApiResponse

typealias GetDocumentListResponse = JsonApiResponse.CollectionDocumentWithRelationships<GetDocumentResponseAttributes, GetDocumentResponseRelationships>

fun List<AuthorizationDocument>.toGetResponse(): GetDocumentListResponse = GetDocumentListResponse(
    data = this.map { it.toGetResponse().data },
    links = JsonApiLinks.ResourceObjectLink(DOCUMENTS_PATH)
)
