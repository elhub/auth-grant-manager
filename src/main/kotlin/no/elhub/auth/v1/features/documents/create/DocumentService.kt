package no.elhub.auth.v1.features.documents.create

import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.DocumentLanguage

class DocumentService(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierDocumentActionHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierDocumentActionHandler,
    private val pdfGenerator: PdfGenerator,
    private val pdfSigner: PdfSigner,
) {
    suspend fun createAuthorizationDocument(
        action: DocumentAction,
        externalReference: String?,
        requestedBy: AuthorizationParty,
        requestedFrom: AuthorizationParty,
        requestedTo: AuthorizationParty,
        language: DocumentLanguage,
    ): AuthorizationDocument {
        val context = DocumentActionContext(
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            language = language,
            externalReference = externalReference,
        )
        val details = when (action) {
            is DocumentAction.ChangeOfEnergySupplierForOrganization ->
                changeOfEnergySupplierHandler.validateAndGetDetails(action, context)

            is DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization ->
                moveInAndChangeOfEnergySupplierHandler.validateAndGetDetails(action, context)
        }
        val pdfBytes = pdfSigner.sign(pdfGenerator.generate(details.pdfContent))

        return AuthorizationDocument.new(
            documentType = action.documentType,
            requestedScopes = listOf(action.toRequestedAuthorizationScope()),
            externalReference = externalReference,
            meta = details.meta,
            validTo = details.validTo,
            requestedBy = requestedBy,
            requestedFrom = requestedFrom,
            requestedTo = requestedTo,
            pdfBytes = pdfBytes,
        )
    }
}
