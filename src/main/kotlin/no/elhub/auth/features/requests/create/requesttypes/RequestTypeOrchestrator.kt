package no.elhub.auth.features.requests.create.requesttypes

import no.elhub.auth.features.requests.AuthorizationRequest
import no.elhub.auth.features.requests.create.requesttypes.changeofsupplierconfirmation.ChangeOfSupplierConfirmationRequestTypeHandler

/**
 * Resolves the handler for a given AuthorizationRequest.Type.
 * Exhaustive by design to enforce wiring when new types are added.
 */
class RequestTypeOrchestrator(
    private val changeOfSupplierRequestTypeHandler: ChangeOfSupplierConfirmationRequestTypeHandler,
) {
    fun resolve(type: AuthorizationRequest.Type): RequestTypeHandler =
        when (type) {
            AuthorizationRequest.Type.ChangeOfSupplierConfirmation -> changeOfSupplierRequestTypeHandler
        }
}
