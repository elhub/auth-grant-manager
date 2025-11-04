package no.elhub.auth.features.documents.common

import no.elhub.auth.features.common.AuthorizationParty
import no.elhub.auth.features.documents.AuthorizationDocument
import java.util.UUID

typealias AuthorizationDocumentsResponse = List<AuthorizationDocumentResponse>

fun List<AuthorizationDocument>.toResponse(): AuthorizationDocumentsResponse = this.map { it.toResponse() }
