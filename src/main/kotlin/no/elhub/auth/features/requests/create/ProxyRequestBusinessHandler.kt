package no.elhub.auth.features.requests.create

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.changeofsupplier.ChangeOfSupplierValidationError
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.command.RequestCommand
import no.elhub.auth.features.requests.create.model.CreateRequestModel
import no.elhub.auth.features.requests.create.model.defaultRequestValidTo

class ProxyRequestBusinessHandler(
    private val changeOfSupplierHandler: ChangeOfSupplierBusinessHandler,
) : RequestBusinessHandler {

    override fun validateAndReturnRequestCommand(createRequestModel: CreateRequestModel): Either<ChangeOfSupplierValidationError, RequestCommand> {
        return when (createRequestModel.requestType) {
            AuthorizationRequest.Type.ChangeOfSupplierConfirmation -> changeOfSupplierHandler.validateAndReturnRequestCommand(createRequestModel)
        }
    }

    override fun getGrantProperties(request: AuthorizationRequest): GrantProperties {
        return GrantProperties(
            validFrom = defaultRequestValidTo(),
            validTo = defaultRequestValidTo(),
        )
    }
}
