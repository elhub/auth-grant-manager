package no.elhub.auth.features.requests.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.RequestBusinessHandler
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel

class ProxyRequestBusinessHandler(
    private val changeOfBalanceSupplierHandler: ChangeOfBalanceSupplierBusinessHandler,
    private val moveInAndChangeOfBalanceSupplierHandler: MoveInAndChangeOfBalanceSupplierBusinessHandler,
) : RequestBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<BusinessProcessError, RequestCommand> =
        when (createRequestModel.requestType) {
            AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson -> changeOfBalanceSupplierHandler.validateAndReturnRequestCommand(createRequestModel)

            AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson -> moveInAndChangeOfBalanceSupplierHandler.validateAndReturnRequestCommand(
                createRequestModel
            )
        }

    override fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties =
        when (request.type) {
            AuthorizationRequest.Type.ChangeOfBalanceSupplierForPerson -> changeOfBalanceSupplierHandler.getCreateGrantProperties(request)
            AuthorizationRequest.Type.MoveInAndChangeOfBalanceSupplierForPerson -> moveInAndChangeOfBalanceSupplierHandler.getCreateGrantProperties(request)
        }
}
