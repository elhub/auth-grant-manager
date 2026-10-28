package no.elhub.auth.v1.requesthandlers.movein

import no.elhub.auth.common.auth.AuthorizationParty
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsLookupException
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v1.common.meteringpoints.toRequestedMeteringPointMetadata
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestTypeSpecificDetails
import no.elhub.auth.v1.features.requests.creation.toRequestPreparationError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.requests.creation.MoveInAndChangeOfEnergySupplierRequestHandler as HandlerContract

private const val REQUEST_VALID_DAYS = 28

class MoveInAndChangeOfEnergySupplierRequestHandler(
    private val meteringPointsService: MeteringPointsService,
) : HandlerContract {
    override suspend fun validateAndGetRequestTypeSpecificDetails(
        details: AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization,
        requestedBy: AuthorizationParty,
        requestedPrincipal: AuthorizationParty,
        requestedOf: AuthorizationParty,
    ): RequestTypeSpecificDetails {
        val metadata = MoveInAndChangeOfEnergySupplierRequestMetadata(
            requestedMeteringPoints = try {
                meteringPointsService.getMeteringPointsForOrganisation(
                    meteringPointIds = details.meteringPointIds.ids.map { it.value }.sorted(),
                    elhubInternalId = requestedPrincipal.id,
                )
            } catch (e: MeteringPointsLookupException) {
                throw e.clientError.toRequestPreparationError()
            }.toRequestedMeteringPointMetadata(),
            moveInDate = details.moveInDate,
        )
        return RequestTypeSpecificDetails(
            validTo = Clock.System.now() + REQUEST_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
