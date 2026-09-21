package no.elhub.auth.v1.domain

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import kotlinx.datetime.Instant
import java.util.UUID

data class AuthorizationDocument(
    val id: String,
    val documentType: AuthorizationDocumentType,
    val status: AuthorizationDocumentStatus,
    val appliesTo: List<ResourceConstraint>,
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
)

enum class AuthorizationDocumentStatus {
    Accepted,
    Expired,
    Pending,
    Rejected,
}

data class AuthorizationGrant(
    val id: UUID,
)
