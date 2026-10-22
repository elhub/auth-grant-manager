package no.elhub.auth.v1.documenthandlers.movein

import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsLookupException
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.documenthandlers.common.toRequestedMeteringPointMetadata
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.toDocumentPreparationError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class MoveInAndChangeOfEnergySupplierDocumentHandler(
    private val meteringPointsService: MeteringPointsService,
) : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val metadata = MoveInAndChangeOfEnergySupplierDocumentMetadata(
            requestedMeteringPoints = try {
                meteringPointsService.getMeteringPoints(
                    meteringPointIds = specification.meteringPointIds.ids.map { it.value }.sorted(),
                    elhubInternalId = context.requestedPrincipal.id,
                )
            } catch (e: MeteringPointsLookupException) {
                throw e.clientError.toDocumentPreparationError()
            }.toRequestedMeteringPointMetadata(),
            validFrom = specification.validFrom,
        )
        val requestedMeteringPoint = metadata.requestedMeteringPoints.first()
        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedPrincipal.id}",
                organizationNumber = context.requestedPrincipal.id,
                meteringPointAddress = "Placeholder address",
                meteringPointId = requestedMeteringPoint.id,
                meterNumber = requestedMeteringPoint.meterNumber ?: "Unknown",
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = null,
                moveInDate = specification.validFrom,
            ),
            validTo = Clock.System.now() + DOCUMENT_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
