package no.elhub.auth.v1.features.requests.creation

import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v1.Errors

sealed class RequestPreparationError(override val detail: String) : Errors(detail) {
    class RequestedMeteringPointsNotFound :
        RequestPreparationError("One or more requested metering points could not be found")

    class MeteringPointsServiceFailure(val clientError: ClientError) :
        RequestPreparationError("Failed to fetch requested metering points: $clientError")
}

fun ClientError.toRequestPreparationError(): RequestPreparationError = when (this) {
    ClientError.NotFound -> RequestPreparationError.RequestedMeteringPointsNotFound()
    else -> RequestPreparationError.MeteringPointsServiceFailure(this)
}
