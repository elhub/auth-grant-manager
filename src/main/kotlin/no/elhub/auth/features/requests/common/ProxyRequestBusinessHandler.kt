package no.elhub.auth.features.requests.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.movein.MoveInBusinessHandler
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel

class ProxyRequestBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
    private val moveInHandler: MoveInBusinessHandler,
) : RequestBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<BusinessProcessError, RequestCommand> =
        when (createRequestModel.requestType) {
            AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson -> changeOfSupplierHandler.validateAndReturnRequestCommand(createRequestModel)
            AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInHandler.validateAndReturnRequestCommand(createRequestModel)
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        when (request.type) {
            AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson -> changeOfSupplierHandler.getCreateGrantProperties(request)
            AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInHandler.getCreateGrantProperties(request)
        }
}
