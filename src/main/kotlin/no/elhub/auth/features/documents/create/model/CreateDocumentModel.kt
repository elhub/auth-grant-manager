package no.elhub.auth.features.documents.create.model

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.create.dto.CreateDocumentMeta

data class CreateDocumentModel(
    val documentType: AuthorizationDocument.Type,
    val meta: CreateDocumentMeta,
)
