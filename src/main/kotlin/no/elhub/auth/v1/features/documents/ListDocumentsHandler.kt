package no.elhub.auth.v1.features.documents

import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationGrantSourceType
import no.elhub.auth.v1.repositories.DocumentRepository
import no.elhub.auth.v1.repositories.GrantRepository
import java.util.UUID

class ListDocumentsHandler(
    private val documentRepository: DocumentRepository,
    private val grantRepository: GrantRepository,
) {
    suspend operator fun invoke(
        authorizedParty: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationDocumentStatus>,
    ): Page<AuthorizationDocument> {
        val page = documentRepository.findAndSortByCreatedAt(authorizedParty, pagination, statuses)
        if (page.items.isEmpty()) return page

        val grants = grantRepository.findBySourceIds(
            AuthorizationGrantSourceType.Document,
            page.items.map { UUID.fromString(it.id) },
        )

        return page.copy(
            items = page.items.map { document ->
                document.copy(authorizationGrants = grants[UUID.fromString(document.id)].orEmpty())
            },
        )
    }
}
