package no.elhub.auth.v1.features.documents

import arrow.core.Either
import no.elhub.auth.common.documents.pdf.PdfGenerator
import no.elhub.auth.common.documents.pdf.PdfSigner
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationDocument
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler
import no.elhub.auth.v1.repositories.DocumentRepository

class CreateDocumentHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierDocumentHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierDocumentHandler,
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
    ): Either<DocumentPreparationError, AuthorizationDocument> {
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
        return when (details) {
            is Either.Left -> details

            is Either.Right -> {
                val creationDetails = details.value
                val pdfBytes = pdfSigner.sign(pdfGenerator.generate(creationDetails.pdfContent))

                val document = AuthorizationDocument.new(
                    documentType = specification.documentType,
                    requestedScopes = listOf(specification.toRequestedAuthorizationScope()),
                    externalReference = externalReference,
                    meta = creationDetails.meta,
                    validTo = creationDetails.validTo,
                    requestedBy = requestedBy,
                    requestedPrincipal = requestedPrincipal,
                    requestedOf = requestedOf,
                    pdfBytes = pdfBytes,
                )
                Either.Right(documentRepository.insert(document))
            }
        }
    }
}
