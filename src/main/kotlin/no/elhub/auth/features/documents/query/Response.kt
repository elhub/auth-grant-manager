package no.elhub.auth.features.documents.query

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.get.GetDocumentResponse
import no.elhub.auth.features.documents.get.toGetResponse

typealias AuthorizationDocumentsResponse = List<GetDocumentResponse>

fun List<AuthorizationDocument>.toGetResponse(): AuthorizationDocumentsResponse = this.map { it.toGetResponse() }
