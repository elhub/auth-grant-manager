package no.elhub.auth.v1.requesthandlers.supplierchange

import no.elhub.auth.common.structuredata.organisations.IdentificationType
import no.elhub.auth.common.structuredata.organisations.PartyType
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsLookupException
import no.elhub.auth.v0.features.businessprocesses.structuredata.meteringpoints.MeteringPointsService
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.common.meteringpoints.toRequestedMeteringPointMetadata
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError
import no.elhub.auth.v1.features.requests.creation.RequestTypeSpecificDetails
import no.elhub.auth.v1.features.requests.creation.toRequestPreparationError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.requests.creation.ChangeOfEnergySupplierRequestHandler as HandlerContract

private const val REQUEST_VALID_DAYS = 28

class ChangeOfEnergySupplierRequestHandler(
    private val meteringPointsService: MeteringPointsService,
    private val organisationsService: OrganisationsService,
) : HandlerContract {
    override suspend fun validateAndGetRequestTypeSpecificDetails(
        details: AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization,
        requestedBy: AuthorizationAgent,
        requestedPrincipal: AuthorizationAgent,
        requestedOf: AuthorizationAgent,
    ): RequestTypeSpecificDetails {
        val requestedPrincipalElhubInternalId = organisationsService
            .getIdentificationByOrganisationNumber(requestedPrincipal.id)
            .fold(
                ifLeft = { throw RequestPreparationError.RequestedPrincipalLookupFailure(it) },
                ifRight = { response ->
                    response.data.firstOrNull { it.attributes?.identificationType == IdentificationType.ORGANIZATION }?.id
                        ?: throw RequestPreparationError.RequestedPrincipalLookupFailure(ClientError.NotFound)
                },
            )
        val meteringPoints = try {
            meteringPointsService.getMeteringPointsForOrganisation(
                meteringPointIds = details.meteringPointIds.ids.map { it.value }.sorted(),
                elhubInternalId = requestedPrincipalElhubInternalId,
            )
        } catch (e: MeteringPointsLookupException) {
            throw e.clientError.toRequestPreparationError()
        }

        val balanceSupplierName = organisationsService.getPartyByIdAndPartyType(partyId = requestedBy.id, partyType = PartyType.BalanceSupplier)
            .fold(
                ifLeft = { throw RequestPreparationError.RequestedByLookupFailure(it) },
                ifRight = { response ->
                    response.data.attributes?.name ?: throw RequestPreparationError.RequestedByLookupFailure(ClientError.NotFound)
                }
            )

        val organisationName = organisationsService.getNameByOrganisationNumber(requestedPrincipal.id).fold(
            ifLeft = { throw RequestPreparationError.RequestedPrincipalNameLookupFailure(it) },
            ifRight = { response ->
                response.data.attributes?.name ?: throw RequestPreparationError.RequestedPrincipalNameLookupFailure(ClientError.NotFound)
            }
        )

        val metadata = ChangeOfEnergySupplierRequestMetadata(
            requestedMeteringPoints = meteringPoints.toRequestedMeteringPointMetadata(),
            balanceSupplierName = balanceSupplierName,
            organizationName = organisationName,
        )
        return RequestTypeSpecificDetails(
            validTo = Clock.System.now() + REQUEST_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
