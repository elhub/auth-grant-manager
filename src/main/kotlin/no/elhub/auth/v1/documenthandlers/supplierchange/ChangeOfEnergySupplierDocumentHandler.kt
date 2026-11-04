package no.elhub.auth.v1.documenthandlers.supplierchange

import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.structuredata.organisations.IdentificationType
import no.elhub.auth.common.structuredata.organisations.PartyType
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsLookupException
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.v1.common.meteringpoints.toPdfContent
import no.elhub.auth.v1.common.meteringpoints.toRequestedMeteringPointMetadata
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import no.elhub.auth.v1.features.documents.creation.toDocumentPreparationError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class ChangeOfEnergySupplierDocumentHandler(
    private val meteringPointsService: MeteringPointsService,
    private val organisationsService: OrganisationsService,
) : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.ChangeOfEnergySupplierForOrganization,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val requestedPrincipalElhubInternalId = organisationsService
            .getIdentificationByOrganisationNumber(context.requestedPrincipal.id)
            .fold(
                ifLeft = { throw DocumentPreparationError.RequestedPrincipalLookupFailure(it) },
                ifRight = { response ->
                    response.data.firstOrNull { it.attributes?.identificationType == IdentificationType.ORGANIZATION }?.id
                        ?: throw DocumentPreparationError.RequestedPrincipalLookupFailure(ClientError.NotFound)
                },
            )
        val meteringPoints = try {
            meteringPointsService.getMeteringPointsForOrganisation(
                meteringPointIds = specification.meteringPointIds.ids.map { it.value }.sorted(),
                elhubInternalId = requestedPrincipalElhubInternalId,
            )
        } catch (e: MeteringPointsLookupException) {
            throw e.clientError.toDocumentPreparationError()
        }

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

        val metadata = ChangeOfEnergySupplierDocumentMetadata(
            requestedMeteringPoints = meteringPoints.toRequestedMeteringPointMetadata(),
            balanceSupplierName = balanceSupplierName,
            organizationName = organisationName,
        )

        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = organisationName,
                organizationNumber = context.requestedPrincipal.id,
                meteringPoints = metadata.requestedMeteringPoints.map { it.toPdfContent() },
                balanceSupplierName = balanceSupplierName,
                agreementReference = null,
            ),
            validTo = Clock.System.now() + DOCUMENT_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
