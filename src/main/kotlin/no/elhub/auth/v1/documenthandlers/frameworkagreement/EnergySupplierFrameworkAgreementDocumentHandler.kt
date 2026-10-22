package no.elhub.auth.v1.documenthandlers.frameworkagreement

import arrow.core.Either
import arrow.core.right
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v1.common.now
import no.elhub.auth.v1.common.todayOslo
import no.elhub.auth.v1.documenthandlers.common.toPdfLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import no.elhub.auth.v1.features.documents.creation.DocumentCreationContext
import no.elhub.auth.v1.features.documents.creation.DocumentCreationDetails
import no.elhub.auth.v1.features.documents.creation.DocumentPreparationError
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.documents.creation.EnergySupplierFrameworkAgreementDocumentHandler as HandlerContract

private const val DOCUMENT_VALID_DAYS = 28

class EnergySupplierFrameworkAgreementDocumentHandler : HandlerContract {
    override suspend fun prepare(
        specification: DocumentSpecification.EnergySupplierFrameworkAgreement,
        context: DocumentCreationContext,
    ): Either<DocumentPreparationError, DocumentCreationDetails> {
        val now = now()

        return DocumentCreationDetails(
            pdfContent = AuthorizationDocumentPdfContent.EnergySupplierFrameworkAgreement(
                language = context.language.toPdfLanguage(),
                organizationName = "Placeholder organization ${context.requestedPrincipal.id}",
                organizationNumber = context.requestedPrincipal.id,
                balanceSupplierName = "Placeholder supplier ${context.requestedBy.id}",
                agreementReference = context.externalReference ?: "Placeholder agreement reference",
                startDate = todayOslo(specification.requestedGrantValidFrom ?: now),
                endDate = specification.requestedGrantValidTo?.let(::todayOslo),
            ),
            validTo = now + DOCUMENT_VALID_DAYS.days,
            meta = buildJsonObject {
                put("placeholder", true)
            },
        ).right()
    }
}
