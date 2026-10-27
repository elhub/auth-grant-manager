package no.elhub.auth.v1.documenthandlers.frameworkagreement

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.EnergySupplierFrameworkAgreementDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class EnergySupplierFrameworkAgreementDocumentHandler : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.EnergySupplierFrameworkAgreement,
        context: DocumentCreationContext,
    ): DocumentCreationDetails {
        val now = Clock.System.now()
        val timeZone = TimeZone.of("Europe/Oslo")

        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedPrincipal.id}",
                organizationNumber = context.requestedPrincipal.id,
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = context.externalReference ?: "Placeholder agreement reference",
                startDate = (specification.requestedGrantValidFrom ?: now).toLocalDateTime(timeZone).date,
                endDate = specification.requestedGrantValidTo?.toLocalDateTime(timeZone)?.date,
            ),
            validTo = now + DOCUMENT_VALID_DAYS.days,
            meta = buildJsonObject {
                put("placeholder", true)
            },
        )
    }
}
