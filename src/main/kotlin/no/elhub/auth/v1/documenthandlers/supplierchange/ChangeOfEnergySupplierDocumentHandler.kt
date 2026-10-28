package no.elhub.auth.v1.documenthandlers.supplierchange

import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsLookupException
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.common.meteringpoints.toRequestedMeteringPointMetadata
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.toDocumentPreparationError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.ChangeOfEnergySupplierDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class ChangeOfEnergySupplierDocumentHandler(
    private val meteringPointsService: MeteringPointsService,
) : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.ChangeOfEnergySupplierForOrganization,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val metadata = ChangeOfEnergySupplierDocumentMetadata(
            requestedMeteringPoints = try {
                meteringPointsService.getMeteringPointsForOrganisation(
                    meteringPointIds = specification.meteringPointIds.ids.map { it.value }.sorted(),
                    elhubInternalId = context.requestedPrincipal.id,
                )
            } catch (e: MeteringPointsLookupException) {
                throw e.clientError.toDocumentPreparationError()
            }.toRequestedMeteringPointMetadata(),
        )
        val requestedMeteringPoint = metadata.requestedMeteringPoints.first()
        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedPrincipal.id}",
                organizationNumber = context.requestedPrincipal.id,
                meteringPointAddress = "Placeholder address",
                meteringPointId = requestedMeteringPoint.id,
                meterNumber = requestedMeteringPoint.meterNumber ?: "Unknown",
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = null,
            ),
            validTo = Clock.System.now() + DOCUMENT_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
