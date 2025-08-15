package no.elhub.auth.features.documents.confirm

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.confirm.ConfirmDocumentCommand
import no.elhub.auth.features.documents.create.SigningService

class ConfirmDocumentHandler(
    private val signingService: SigningService,
    private val documentRepository: DocumentRepository,
) {
    suspend operator fun invoke(command: ConfirmDocumentCommand): AuthorizationDocument {
        throw NotImplementedError()
    }
}
