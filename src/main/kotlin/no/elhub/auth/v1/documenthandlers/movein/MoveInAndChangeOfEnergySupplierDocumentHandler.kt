package no.elhub.auth.v1.documenthandlers.movein

import arrow.core.getOrElse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.toDocumentPreparationError
import no.elhub.auth.v1.features.documents.creation.toJsonObject
import no.elhub.auth.v1.features.documents.creation.toRequestedMeteringPointsMetadata
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
        val meteringPoints = meteringPointsService.getMeteringPoints(
            meteringPointIds = specification.meteringPointIds.ids.map { it.value }.sorted(),
            elhubInternalId = context.requestedPrincipal.id,
        )
            .getOrElse { throw it.toDocumentPreparationError() }
            .toRequestedMeteringPointsMetadata()
        val requestedMeteringPoint = meteringPoints.requestedMeteringPoints.first()
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
            meta = JsonObject(
                meteringPoints.toJsonObject() +
                    ("validFrom" to JsonPrimitive(specification.validFrom.toString())),
            ),
        )
    }
}
