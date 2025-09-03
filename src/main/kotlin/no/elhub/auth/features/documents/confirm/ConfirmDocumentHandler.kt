package no.elhub.auth.features.documents.confirm

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.confirm.ConfirmDocumentCommand
import no.elhub.auth.features.documents.create.DocumentSigningService

class ConfirmDocumentHandler(
    private val signingService: DocumentSigningService,
    private val documentRepository: DocumentRepository,
) {
    operator fun invoke(command: ConfirmDocumentCommand): AuthorizationDocument {
        throw NotImplementedError()
    }
}
