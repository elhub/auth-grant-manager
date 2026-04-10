package no.elhub.auth.features.requests.common

import arrow.core.Either
import kotlinx.datetime.LocalDate
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofbalancesupplier.ChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofbalancesupplier.MoveInAndChangeOfBalanceSupplierBusinessHandler
import no.elhub.auth.features.common.party.AuthorizationParty
import no.elhub.auth.features.grants.common.CreateGrantProperties
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand

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

data class CreateRequestBusinessModel(
    val authorizedParty: AuthorizationParty,
    val requestType: AuthorizationRequest.Type,
    val requestedBy: AuthorizationParty,
    val requestedFrom: AuthorizationParty,
    val requestedTo: AuthorizationParty,
    val meta: CreateRequestBusinessMeta,
)

data class CreateRequestBusinessMeta(
    val requestedFromName: String,
    val requestedForMeteringPointId: String,
    val requestedForMeteringPointAddress: String,
    val balanceSupplierName: String,
    val balanceSupplierContractName: String,
    val moveInDate: LocalDate? = null,
    val redirectURI: String? = null,
)
