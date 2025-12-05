package no.elhub.auth.features.requests.create

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
import no.elhub.auth.features.businessprocesses.changeofsupplier.toChangeOfSupplierBusinessModel
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.command.toRequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel

class RequestBusinessOrchestrator(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
) {
    fun handle(model: CreateRequestModel): Either<ChangeOfSupplierValidationError, RequestCommand> =
        when (model.requestType) {
            AuthorizationRequest.Type.ChangeOfSupplierConfirmation ->
                changeOfSupplierHandler.handle(model.toChangeOfSupplierBusinessModel()).map { it.toRequestCommand(model.validTo) }
        }
}
