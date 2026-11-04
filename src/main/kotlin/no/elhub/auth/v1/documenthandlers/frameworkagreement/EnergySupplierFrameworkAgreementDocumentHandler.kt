package no.elhub.auth.v1.documenthandlers.frameworkagreement

import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.structuredata.organisations.PartyType
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.common.toOsloDate
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.EnergySupplierFrameworkAgreementDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class EnergySupplierFrameworkAgreementDocumentHandler(private val organisationsService: OrganisationsService) : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.EnergySupplierFrameworkAgreement,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val now = now()

        val balanceSupplierName = organisationsService
            .getPartyByIdAndPartyType(partyId = context.requestedBy.id, partyType = PartyType.BalanceSupplier)
            .fold(
                ifLeft = { throw DocumentPreparationError.RequestedByLookupFailure(it) },
                ifRight = { response ->
                    response.data.attributes?.name ?: throw DocumentPreparationError.RequestedByLookupFailure(ClientError.NotFound)
                }
            )

        val organisationName = organisationsService.getNameByOrganisationNumber(context.requestedPrincipal.id).fold(
            ifLeft = { throw DocumentPreparationError.RequestedPrincipalNameLookupFailure(it) },
            ifRight = { response ->
                response.data.attributes?.name ?: throw DocumentPreparationError.RequestedPrincipalNameLookupFailure(ClientError.NotFound)
            }
        )

        val metadata = EnergySupplierFrameworkAgreementDocumentMetadata(balanceSupplierName = balanceSupplierName, organizationName = organisationName)

        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement(
                language = context.language.toPdfLanguage(),
                organizationName = organisationName,
                organizationNumber = context.requestedPrincipal.id,
                balanceSupplierName = balanceSupplierName,
                agreementReference = context.externalReference,
                startDate = (specification.requestedGrantValidFrom ?: now).toOsloDate(),
                endDate = specification.requestedGrantValidTo?.toOsloDate(),
            ),
            validTo = now + DOCUMENT_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
