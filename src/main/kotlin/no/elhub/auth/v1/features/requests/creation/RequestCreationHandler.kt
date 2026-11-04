package no.elhub.auth.v1.features.requests.creation

import kotlinx.serialization.json.JsonObject
import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import kotlin.time.Instant

data class RequestTypeSpecificDetails(
    val validTo: Instant,
    val meta: JsonObject,
)

interface RequestCreationHandler<in Details : AuthorizationRequestTypeDetails> {
    suspend fun validateAndGetRequestTypeSpecificDetails(
        details: Details,
        requestedBy: AuthorizationParty,
        requestedPrincipal: AuthorizationParty,
        requestedOf: AuthorizationParty,
    ): RequestTypeSpecificDetails
}

interface ChangeOfEnergySupplierRequestHandler :
    RequestCreationHandler<AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization>

interface MoveInAndChangeOfEnergySupplierRequestHandler :
    RequestCreationHandler<AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization>

interface EnergySupplierFrameworkAgreementRequestHandler :
    RequestCreationHandler<AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement>
