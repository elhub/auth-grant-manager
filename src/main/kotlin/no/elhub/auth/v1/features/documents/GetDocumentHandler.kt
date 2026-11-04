package no.elhub.auth.v1.features.documents

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.auth.v1.repositories.GrantRepository
import java.util.UUID

class GetDocumentHandler(
    private val documentRepository: DocumentRepository,
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
    ): AuthorizationDocument {
        val document = documentRepository.find(id)

        if (authorizedParty != document.requestedBy && authorizedParty != document.requestedOf) {
            throw GetDocumentError.NotAuthorized()
        }

        val grants = grantRepository.findBySourceIds(AuthorizationGrantSourceType.Document, listOf(id))

        return document.copy(authorizationGrants = grants[id].orEmpty())
    }
}
