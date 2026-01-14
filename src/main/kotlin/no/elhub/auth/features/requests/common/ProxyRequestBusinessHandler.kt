package no.elhub.auth.features.requests.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.movein.MoveInBusinessHandler
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.requesttypes.RequestTypeValidationError

class ProxyRequestBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
    private val moveInHandler: MoveInBusinessHandler,
) : RequestBusinessHandler {
    override fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<RequestTypeValidationError, RequestCommand> =
        when (createRequestModel.requestType) {
            AuthorizationRequest.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.validateAndReturnRequestCommand(createRequestModel)
            AuthorizationRequest.Type.MoveIn -> moveInHandler.validateAndReturnRequestCommand(createRequestModel)
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        when (request.type) {
            AuthorizationRequest.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.getCreateGrantProperties(request)
            AuthorizationRequest.Type.MoveIn -> moveInHandler.getCreateGrantProperties(request)
        }
}
