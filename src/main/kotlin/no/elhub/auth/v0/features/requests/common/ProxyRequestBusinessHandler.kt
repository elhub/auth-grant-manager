package no.elhub.auth.v0.features.requests.common

import arrow.core.Either
import no.elhub.auth.v0.features.businessprocesses.BusinessProcessError
import no.elhub.auth.v0.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.v0.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.v0.features.grants.common.CreateGrantProperties
import no.elhub.auth.v0.features.requests.AuthorizationRequest
import no.elhub.auth.v0.features.requests.create.command.RequestCommand

class ProxyRequestBusinessHandler(
    private val changeOfBalanceSupplierHandler: ChangeOfBalanceSupplierBusinessHandler,
    private val moveInAndChangeOfBalanceSupplierHandler: MoveInAndChangeOfBalanceSupplierBusinessHandler,
) : RequestBusinessHandler {
    override suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestBusinessModel): Either<BusinessProcessError, RequestCommand> =
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

interface RequestBusinessHandler {
    suspend fun validateAndReturnRequestCommand(createRequestModel: CreateRequestBusinessModel): Either<BusinessProcessError, RequestCommand>
    fun getCreateGrantProperties(request: AuthorizationRequest): CreateGrantProperties
}
