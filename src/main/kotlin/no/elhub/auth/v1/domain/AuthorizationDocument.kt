package no.elhub.auth.v1.domain

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class AuthorizationDocument(
    val id: String,
    val documentType: AuthorizationDocumentType,
    val status: AuthorizationDocumentStatus,
    val requestedScopes: List<RequestedAuthorizationScope>,
    val externalReference: String?,
    val validTo: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val signedBy: AuthorizationParty?,
    val authorizationGrants: List<AuthorizationGrant>,
    val pdfBytes: ByteArray,
) {
    companion object {
        fun new(
            documentType: AuthorizationDocumentType,
            requestedScopes: List<RequestedAuthorizationScope>,
            externalReference: String?,
            requestedBy: AuthorizationParty,
            requestedFrom: AuthorizationParty,
            requestedTo: AuthorizationParty,
            pdfBytes: ByteArray,
        ): AuthorizationDocument {
            val now = Clock.System.now()
            return AuthorizationDocument(
                id = UUID.randomUUID().toString(),
                documentType = documentType,
                status = AuthorizationDocumentStatus.Pending,
                requestedScopes = requestedScopes,
                externalReference = externalReference,
                validTo = null,
                createdAt = now,
                updatedAt = now,
                requestedBy = requestedBy,
                requestedFrom = requestedFrom,
                requestedTo = requestedTo,
                signedBy = null,
                authorizationGrants = emptyList(),
                pdfBytes = pdfBytes,
            )
        }
    }
}

enum class AuthorizationDocumentStatus {
    Accepted,
    Expired,
    Pending,
}

enum class AuthorizationDocumentType {
    ChangeOfEnergySupplierForOrganization,
    MoveInAndChangeOfEnergySupplierForOrganization,
}
