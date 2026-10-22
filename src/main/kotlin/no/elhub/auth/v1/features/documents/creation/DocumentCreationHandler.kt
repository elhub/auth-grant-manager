package no.elhub.auth.v1.features.documents.creation

import arrow.core.Either
import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.documents.pdf.AuthorizationDocumentPdfContent
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.DocumentLanguage
import no.elhub.auth.v1.domain.documents.DocumentSpecification
import kotlin.time.Instant

data class DocumentCreationContext(
    val requestedBy: AuthorizationParty,
    val requestedPrincipal: AuthorizationParty,
    val requestedOf: AuthorizationParty,
    val language: DocumentLanguage,
    val externalReference: String?,
)

data class DocumentCreationDetails(
    val pdfContent: AuthorizationDocumentPdfContent,
    val validTo: Instant,
    val meta: JsonObject,
)

sealed interface DocumentPreparationError {
    data object RequestedMeteringPointsNotFound : DocumentPreparationError
    data class MeteringPointsServiceFailure(val cause: ClientError) : DocumentPreparationError
}

fun ClientError.toDocumentPreparationError(): DocumentPreparationError = when (this) {
    ClientError.NotFound -> DocumentPreparationError.RequestedMeteringPointsNotFound
    else -> DocumentPreparationError.MeteringPointsServiceFailure(this)
}

interface DocumentCreationHandler<in Specification : DocumentSpecification> {
    suspend fun prepare(
        specification: Specification,
        context: DocumentCreationContext,
    ): Either<DocumentPreparationError, DocumentCreationDetails>
}

interface ChangeOfEnergySupplierDocumentHandler :
    DocumentCreationHandler<DocumentSpecification.ChangeOfEnergySupplierForOrganization>

interface MoveInAndChangeOfEnergySupplierDocumentHandler :
    DocumentCreationHandler<DocumentSpecification.MoveInAndChangeOfEnergySupplierForOrganization>
