package no.elhub.auth.v1.features.documents

import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler

class CreateDocumentHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierDocumentHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierDocumentHandler,
    private val pdfGenerator: PdfGenerator,
    private val pdfSigner: PdfSigner,
) {
    suspend fun createAuthorizationDocument(
        specification: DocumentSpecification,
        externalReference: String?,
        requestedBy: AuthorizationParty,
        requestedPrincipal: AuthorizationParty,
        requestedOf: AuthorizationParty,
        language: DocumentLanguage,
    ): AuthorizationDocument {
        val context = DocumentCreationContext(
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            language = language,
            externalReference = externalReference,
        )
        val details = when (specification) {
            is DocumentSpecification.ChangeOfEnergySupplierForOrganization ->
                changeOfEnergySupplierHandler.prepare(specification, context)

            is DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization ->
                moveInAndChangeOfEnergySupplierHandler.prepare(specification, context)
        }
        val pdfBytes = pdfSigner.sign(pdfGenerator.generate(details.pdfContent))

        return AuthorizationDocument.new(
            documentType = specification.documentType,
            requestedScopes = listOf(specification.toRequestedAuthorizationScope()),
            externalReference = externalReference,
            meta = details.meta,
            validTo = details.validTo,
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            pdfBytes = pdfBytes,
        )
    }
}
