package no.elhub.auth.v1.domain

import kotlinx.datetime.Instant
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID

data class AuthorizationDocument(
    val id: String,
    val documentType: AuthorizationDocumentType,
    val status: AuthorizationDocumentStatus,
    val resourceConstraints: List<ResourceConstraint>,
    val allowedChanges: List<ResourceConstraint>,
    val externalReference: String?,
    val validTo: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val signedBy: AuthorizationParty?,
    val authorizationGrant: AuthorizationGrant?,
    val pdfBytes: ByteArray,
) {
    companion object {
        fun new(
            documentType: AuthorizationDocumentType,
            resourceConstraints: List<ResourceConstraint>,
            externalReference: String?,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            pdfBytes: ByteArray,
        ): AuthorizationDocument {
            val now = kotlinx.datetime.Clock.System.now()
            return AuthorizationDocument(
                id = UUID.randomUUID().toString(),
                documentType = documentType,
                status = AuthorizationDocumentStatus.Pending,
                resourceConstraints = resourceConstraints,
                allowedChanges = emptyList(),
                externalReference = externalReference,
                validTo = null,
                createdAt = now,
                updatedAt = now,
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                signedBy = null,
                authorizationGrant = null,
                pdfBytes = pdfBytes,
            )
        }
    }
}

enum class AuthorizationDocumentStatus {
    Accepted,
    Expired,
    Pending,
    Rejected,
}

data class AuthorizationGrant(
    val id: UUID,
)
