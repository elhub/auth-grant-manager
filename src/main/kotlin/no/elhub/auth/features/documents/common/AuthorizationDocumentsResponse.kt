package no.elhub.auth.features.documents.common

import no.elhub.auth.features.documents.AuthorizationDocument

typealias AuthorizationDocumentsResponse = List<AuthorizationDocumentResponse>

fun List<AuthorizationDocument>.toResponse() = this.map { it.toResponse() }
