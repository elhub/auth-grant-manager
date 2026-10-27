package no.elhub.auth.v1.domain

import kotlinx.serialization.json.JsonObject
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
    val meta: JsonObject,
    val requestedGrantValidFrom: Instant? = null,
    val requestedGrantValidTo: Instant? = null,
    val validFrom: Instant,
    val validTo: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val requestedBy: AuthorizationParty,
    val requestedPrincipal: AuthorizationParty,
    val requestedOf: AuthorizationParty,
    val approvedBy: AuthorizationParty?,
    val authorizationGrants: List<AuthorizationGrant>?,
    val pdfBytes: ByteArray,
) {
    companion object {
        fun new(
            documentType: AuthorizationDocumentType,
            requestedScopes: List<RequestedAuthorizationScope>,
            externalReference: String?,
            meta: JsonObject,
            validTo: Instant,
            requestedBy: AuthorizationParty,
            requestedPrincipal: AuthorizationParty,
            requestedOf: AuthorizationParty,
            pdfBytes: ByteArray,
            requestedGrantValidFrom: Instant? = null,
            requestedGrantValidTo: Instant? = null,
        ): AuthorizationDocument {
            val now = Clock.System.now()
            return AuthorizationDocument(
                id = UUID.randomUUID().toString(),
                documentType = documentType,
                status = AuthorizationDocumentStatus.Pending,
                requestedScopes = requestedScopes,
                externalReference = externalReference,
                meta = meta,
                requestedGrantValidFrom = requestedGrantValidFrom,
                requestedGrantValidTo = requestedGrantValidTo,
                validFrom = now,
                validTo = validTo,
                createdAt = now,
                updatedAt = now,
                requestedBy = requestedBy,
                requestedPrincipal = requestedPrincipal,
                requestedOf = requestedOf,
                approvedBy = null,
                authorizationGrants = null,
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
    EnergySupplierFrameworkAgreement,
    ChangeOfEnergySupplierForOrganization,
    MoveInAndChangeOfEnergySupplierForOrganization,
}
