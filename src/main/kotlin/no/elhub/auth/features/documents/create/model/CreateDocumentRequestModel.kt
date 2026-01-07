package no.elhub.auth.features.documents.create.model

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentRequestMeta

data class CreateDocumentRequestModel(
    val documentType: AuthorizationDocument.Type,
    val meta: CreateDocumentRequestMeta,
)
