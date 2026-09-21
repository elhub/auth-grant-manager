package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocumentType
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.AuthorizationDocumentStatus
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.AuthorizationScope
import kotlinx.datetime.Clock

data class DocumentInput(
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val requestedBy: AuthorizationParty,
    val documentType: AuthorizationDocumentType,
    val language: DocumentLanguage,
    val scopes: List<AuthorizationScope>,
    val externalReference: String?,
    val pdfBytes: ByteArray,
)

class CreateDocumentCoreHandler {
    suspend operator fun invoke(input: DocumentInput): AuthorizationDocument {
        val now = Clock.System.now()
        return AuthorizationDocument(
            id = "authorization-document-${now.toEpochMilliseconds()}",
            documentType = input.documentType,
            status = AuthorizationDocumentStatus.Pending,
            appliesTo = input.scopes.flatMap { it.appliesTo },
            allowedChanges = emptyList(),
            externalReference = input.externalReference,
            validTo = null,
            createdAt = now,
            updatedAt = now,
            requestedBy = input.requestedBy,
            requestedFrom = input.requestedFrom,
            requestedTo = input.requestedTo,
            signedBy = null,
            authorizationGrant = null,
            pdfBytes = input.pdfBytes,
        )
    }
}
