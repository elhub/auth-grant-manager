package no.elhub.auth.features.documents.create

import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.zipOrAccumulate
import no.elhub.auth.features.documents.AuthorizationDocument

class Command private constructor(
    val type: AuthorizationDocument.Type,
    val requestedBy: String,
    val requestedTo: String,
    val meteringPoint: String
) {
    companion object {
        operator fun invoke(
            type: AuthorizationDocument.Type,
            requestedBy: String,
            requestedTo: String,
            meteringPoint: String,
        ) = either {
            // https://arrow-kt.io/learn/typed-errors/validation/#fail-first-vs-accumulation
            zipOrAccumulate(
                { ensure(requestedBy.isNotBlank()) { ValidationError.MissingRequestedBy } },
                { ensure(requestedTo.isNotBlank()) { ValidationError.MissingRequestedTo } },
                { ensure(meteringPoint.isNotBlank()) { ValidationError.MissingMeteringPoint } },
            ) { _, _, _ -> }

            Command(
                type,
                requestedBy,
                requestedTo,
                meteringPoint,
            )
        }
    }
}

sealed class ValidationError {
    data object MissingRequestedBy : ValidationError()
    data object MissingRequestedTo : ValidationError()
    data object MissingMeteringPoint : ValidationError()
}
