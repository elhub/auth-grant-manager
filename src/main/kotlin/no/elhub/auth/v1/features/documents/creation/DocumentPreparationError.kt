package no.elhub.auth.v1.features.documents.creation

import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v1.Errors

sealed class DocumentPreparationError(override val detail: String) : Errors(detail) {
    class RequestedMeteringPointsNotFound :
        DocumentPreparationError("One or more requested metering points could not be found")

    class MeteringPointsServiceFailure(val clientError: ClientError) :
        DocumentPreparationError("Failed to fetch requested metering points: $clientError")
}

fun ClientError.toDocumentPreparationError(): DocumentPreparationError = when (this) {
    ClientError.NotFound -> DocumentPreparationError.RequestedMeteringPointsNotFound()
    else -> DocumentPreparationError.MeteringPointsServiceFailure(this)
}
