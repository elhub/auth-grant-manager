package no.elhub.auth.v1.requesthandlers.movein

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestTypeSpecificDetails
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.requests.creation.MoveInAndChangeOfEnergySupplierRequestHandler as HandlerContract

private const val REQUEST_VALID_DAYS = 28

class MoveInAndChangeOfEnergySupplierRequestHandler : HandlerContract {
    override suspend fun validateAndGetRequestTypeSpecificDetails(
        details: AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization,
        requestedBy: AuthorizationParty,
        requestedPrincipal: AuthorizationParty,
        requestedOf: AuthorizationParty,
    ): RequestTypeSpecificDetails =
        RequestTypeSpecificDetails(
            validTo = Clock.System.now() + REQUEST_VALID_DAYS.days,
            meta = buildJsonObject {
                put("placeholder", true)
            },
        )
}
