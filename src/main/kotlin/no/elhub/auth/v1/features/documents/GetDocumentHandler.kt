package no.elhub.auth.v1.features.documents

import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.auth.v1.repositories.GrantRepository
import kotlin.uuid.Uuid

class GetDocumentHandler(
    private val documentRepository: DocumentRepository,
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        id: Uuid,
        authorizedAgent: AuthorizationAgent,
    ): AuthorizationDocument {
        val document = documentRepository.find(id)

        if (authorizedAgent != document.requestedBy && authorizedAgent != document.requestedPrincipal) {
            throw GetDocumentError.NotAuthorized()
        }

        val grants = grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(id))

        return document.copy(authorizationGrants = grants[id].orEmpty())
    }
}
