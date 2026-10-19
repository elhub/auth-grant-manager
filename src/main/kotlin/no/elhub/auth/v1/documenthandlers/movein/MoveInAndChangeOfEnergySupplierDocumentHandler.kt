package no.elhub.auth.v1.documenthandlers.movein

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.MoveInAndChangeOfEnergySupplierDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class MoveInAndChangeOfEnergySupplierDocumentHandler : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val meteringPointId = specification.meteringPointIds.ids.first().value

        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedPrincipal.id}",
                organizationNumber = context.requestedPrincipal.id,
                meteringPointAddress = "Placeholder address",
                meteringPointId = meteringPointId,
                meterNumber = "Unknown",
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = null,
                moveInDate = specification.validFrom,
            ),
            validTo = Clock.System.now() + DOCUMENT_VALID_DAYS.days,
            meta = buildJsonObject {
                put("meteringPointId", meteringPointId)
                put("validFrom", specification.validFrom.toString())
                put("placeholder", true)
            },
        )
    }
}
