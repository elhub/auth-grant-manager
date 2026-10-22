package no.elhub.auth.v1.documenthandlers.supplierchange

import arrow.core.Either
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import no.elhub.auth.v1.features.documents.creation.toDocumentPreparationError
import no.elhub.auth.v1.features.documents.creation.toJsonObject
import no.elhub.auth.v1.features.documents.creation.toRequestedMeteringPointsMetadata
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
    ): Either<DocumentPreparationError, DocumentCreationDetails> {
        val requestedMeteringPoints = meteringPointsService.getMeteringPoints(
            meteringPointIds = specification.meteringPointIds.ids.map { it.value }.sorted(),
            elhubInternalId = context.requestedPrincipal.id,
        )
            .mapLeft { it.toDocumentPreparationError() }
            .map { it.toRequestedMeteringPointsMetadata() }
        return requestedMeteringPoints.map { meteringPoints ->
            val requestedMeteringPoint = meteringPoints.requestedMeteringPoints.first()
            DocumentCreationDetails(
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
                meta = meteringPoints.toJsonObject(),
            )
        }
    }
}
