package no.elhub.auth.v1.features.documents.create

import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.DocumentLanguage
import kotlin.time.Instant

data class DocumentCreationContext(
    val requestedBy: AuthorizationParty,
    val requestedPrincipal: AuthorizationParty,
    val requestedOf: AuthorizationParty,
    val language: DocumentLanguage,
    val externalReference: String?,
)

data class DocumentCreationDetails(
    val pdfContent: AuthorizationDocumentPdfContent,
    val validTo: Instant,
    val meta: JsonObject,
)

interface DocumentSpecificationHandler<in Specification : DocumentSpecification> {
    suspend fun validateAndGetDetails(
        specification: Specification,
        context: DocumentCreationContext,
    ): DocumentCreationDetails
}

interface ChangeOfEnergySupplierDocumentHandler :
    DocumentSpecificationHandler<DocumentSpecification.ChangeOfEnergySupplierForOrganization>

interface MoveInAndChangeOfEnergySupplierDocumentHandler :
    DocumentSpecificationHandler<DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization>
