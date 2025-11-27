package no.elhub.auth.features.requests.create.requesttypes

import arrow.core.Either
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel

/**
 * Handler for a specific AuthorizationRequest.Type. Validates and maps to a RequestCommand.
 */
interface RequestTypeHandler {
    suspend fun handle(meta: CreateRequestModel): Either<RequestTypeValidationError, RequestCommand>
}
