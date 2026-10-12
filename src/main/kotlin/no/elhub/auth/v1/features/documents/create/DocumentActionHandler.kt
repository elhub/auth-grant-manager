package no.elhub.auth.v1.features.documents.create

import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.DocumentLanguage
import kotlin.time.Instant

data class DocumentActionContext(
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val language: DocumentLanguage,
    val externalReference: String?,
)

data class DocumentActionDetails(
    val pdfContent: AuthorizationDocumentPdfContent,
    val validTo: Instant,
    val meta: JsonObject,
)

interface DocumentActionHandler<in Action : DocumentAction> {
    suspend fun validateAndGetDetails(
        action: Action,
        context: DocumentActionContext,
    ): DocumentActionDetails
}

interface ChangeOfEnergySupplierDocumentActionHandler :
    DocumentActionHandler<DocumentAction.ChangeOfEnergySupplierForOrganization>

interface MoveInAndChangeOfEnergySupplierDocumentActionHandler :
    DocumentActionHandler<DocumentAction.MoveInAndChangeOfEnergySupplierForOrganization>
