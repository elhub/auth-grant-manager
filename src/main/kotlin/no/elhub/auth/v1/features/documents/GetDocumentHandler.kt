package no.elhub.auth.v1.features.documents

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import java.util.UUID

interface GetDocumentHandler {
    suspend operator fun invoke(
        id: UUID,
        authorizedParty: AuthorizationParty,
    ): AuthorizationDocument
}
