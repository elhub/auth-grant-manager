package no.elhub.auth.features.requests.common

import no.elhub.auth.features.businessprocesses.changeofenergysupplier.ChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.businessprocesses.moveinandchangeofenergysupplier.MoveInAndChangeOfEnergySupplierBusinessHandler
import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.update.GrantBusinessHandler

class ProxyGrantBusinessHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierBusinessHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierBusinessHandler,
) : GrantBusinessHandler {
    override fun getMetaProperties(request: AuthorizationRequest): List<String> {
        return when(request.type) {
            AuthorizationRequest.Type.ChangeOfEnergySupplierForPerson -> changeOfEnergySupplierHandler.getMetaProperties(request)
            AuthorizationRequest.Type.MoveInAndChangeOfEnergySupplierForPerson -> moveInAndChangeOfEnergySupplierHandler.getMetaProperties(request)
        }
    }
}
