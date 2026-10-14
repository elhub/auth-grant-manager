package no.elhub.auth.v1.repositories

import no.elhub.auth.v0.features.common.Page
import no.elhub.auth.v0.features.common.Pagination
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.AuthorizationGrant
import java.util.UUID

interface DocumentRepository {
    suspend fun find(id: UUID): AuthorizationDocument

    suspend fun insert(document: AuthorizationDocument): AuthorizationDocument

    suspend fun findAndSortByCreatedAt(
        requestedBy: AuthorizationParty,
        pagination: Pagination,
        statuses: List<AuthorizationDocumentStatus>,
    ): Page<AuthorizationDocument>

    suspend fun confirm(
        documentId: UUID,
        signedFile: ByteArray,
        signatory: AuthorizationParty,
        grant: AuthorizationGrant,
    ): AuthorizationDocument
}
