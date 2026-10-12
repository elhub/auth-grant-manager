package no.elhub.auth.v1.features.documents.create

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.common.documents.pdf.PdfLanguage
import no.elhub.auth.v1.domain.DocumentLanguage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private const val DOCUMENT_VALID_DAYS = 28

class PlaceholderChangeOfEnergySupplierDocumentActionHandler : ChangeOfEnergySupplierDocumentActionHandler {
    override suspend fun validateAndGetDetails(
        action: DocumentAction.ChangeOfEnergySupplierForOrganization,
        context: DocumentActionContext,
    ): DocumentActionDetails {
        val meteringPointId = action.meteringPointIds.ids.first().value

        return DocumentActionDetails(
            pdfContent = AuthorizationDocumentPdfContent.ChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedFrom.id}",
                organizationNumber = context.requestedFrom.id,
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

class PlaceholderMoveInAndChangeOfEnergySupplierDocumentActionHandler : MoveInAndChangeOfEnergySupplierDocumentActionHandler {
    override suspend fun validateAndGetDetails(
        action: DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization,
        context: DocumentActionContext,
    ): DocumentActionDetails {
        val meteringPointId = action.meteringPointIds.ids.first().value

        return DocumentActionDetails(
            pdfContent = AuthorizationDocumentPdfContent.MoveInAndChangeOfBalanceSupplierForOrganisation(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedFrom.id}",
                organizationNumber = context.requestedFrom.id,
                meteringPointAddress = "Placeholder address",
                meteringPointId = meteringPointId,
                meterNumber = "Unknown",
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = null,
                moveInDate = action.validFrom,
            ),
            validTo = Clock.System.now() + DOCUMENT_VALID_DAYS.days,
            meta = buildJsonObject {
                put("meteringPointId", meteringPointId)
                put("validFrom", action.validFrom.toString())
                put("placeholder", true)
            },
        )
    }
}

private fun DocumentLanguage.toPdfLanguage() = when (this) {
    DocumentLanguage.Nb -> PdfLanguage.NB
    DocumentLanguage.Nn -> PdfLanguage.NN
    DocumentLanguage.En -> PdfLanguage.EN
}
