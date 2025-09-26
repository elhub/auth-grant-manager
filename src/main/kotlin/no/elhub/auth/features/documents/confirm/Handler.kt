package no.elhub.auth.features.documents.confirm

import no.elhub.auth.features.documents.AuthorizationDocument
import no.elhub.auth.features.documents.common.DocumentRepository
import no.elhub.auth.features.documents.create.FileSigningService

class Handler(
    private val signingService: FileSigningService,
    private val documentRepository: DocumentRepository,
) {
    operator fun invoke(command: Command): AuthorizationDocument = throw NotImplementedError()
}
