package no.elhub.auth.features.requests.common

import arrow.core.Either
import no.elhub.auth.features.businessprocesses.BusinessProcessError
import no.elhub.auth.features.businessprocesses.changeofenergysupplier.ChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.MoveInAndChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.update.GrantBusinessHandler

class ProxyGrantBusinessHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierBusinessHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierBusinessHandler,
) : GrantBusinessHandler {
    override fun getUpdateGrantMetaProperties(request: AuthorizationRequest): Either<BusinessProcessError, Map<String, String>> = when (request.type) {
        AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson -> changeOfEnergySupplierHandler.getUpdateGrantMetaProperties(request)
        AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInAndChangeOfEnergySupplierHandler.getUpdateGrantMetaProperties(request)
    }
}
