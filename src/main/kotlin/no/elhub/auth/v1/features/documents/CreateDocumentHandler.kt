package no.elhub.auth.v1.features.documents

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.EnergySupplierFrameworkAgreementDocumentHandler
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.repositories.DocumentRepository

class CreateDocumentHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierDocumentHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierDocumentHandler,
    private val energySupplierFrameworkAgreementHandler: EnergySupplierFrameworkAgreementDocumentHandler,
    private val pdfGenerator: PdfGenerator,
    private val pdfSigner: PdfSigner,
    private val documentRepository: DocumentRepository,
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

            is DocumentSpecification.EnergySupplierFrameworkAgreement ->
                energySupplierFrameworkAgreementHandler.prepare(specification, context)
        }
        val pdfBytes = pdfSigner.sign(pdfGenerator.generate(details.pdfContent))

        val document = AuthorizationDocument.create(
            specification = specification,
            externalReference = externalReference,
            meta = details.meta,
            validTo = details.validTo,
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            pdfBytes = pdfBytes,
        )
        return documentRepository.insert(document)
    }
}
