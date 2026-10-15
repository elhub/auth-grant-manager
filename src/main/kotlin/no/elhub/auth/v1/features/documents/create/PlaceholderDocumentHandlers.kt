package no.elhub.auth.v1.features.documents.create

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.documents.pdf.PdfLanguage
import no.elhub.auth.v1.domain.DocumentLanguage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private const val DOCUMENT_VALID_DAYS = 28

class PlaceholderChangeOfEnergySupplierDocumentHandler : ChangeOfEnergySupplierDocumentHandler {
    override suspend fun validateAndGetDetails(
        specification: DocumentSpecification.ChangeOfEnergySupplierForOrganization,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val meteringPointId = specification.meteringPointIds.ids.first().value

        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedPrincipal.id}",
                organizationNumber = context.requestedPrincipal.id,
                meteringPointAddress = "Placeholder address",
                meteringPointId = meteringPointId,
                meterNumber = "Unknown",
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = null,
            ),
            validTo = Clock.System.now() + DOCUMENT_VALID_DAYS.days,
            meta = buildJsonObject {
                put("meteringPointId", meteringPointId)
                put("placeholder", true)
            },
        )
    }
}

class PlaceholderMoveInAndChangeOfEnergySupplierDocumentHandler : MoveInAndChangeOfEnergySupplierDocumentHandler {
    override suspend fun validateAndGetDetails(
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

private fun DocumentLanguage.toPdfLanguage() = when (this) {
    DocumentLanguage.NB -> PdfLanguage.NB
    DocumentLanguage.NN -> PdfLanguage.NN
    DocumentLanguage.EN -> PdfLanguage.EN
}
