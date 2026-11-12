package no.elhub.auth.v1.requesthandlers.frameworkagreement

import no.elhub.auth.common.structuredata.organisations.PartyType
import no.elhub.auth.v0.features.businessprocesses.structuredata.common.ClientError
import no.elhub.auth.v0.features.businessprocesses.structuredata.organisations.OrganisationsService
import no.elhub.auth.v1.common.auth.AuthorizationAgent
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.RequestPreparationError
import no.elhub.auth.v1.features.requests.creation.RequestTypeSpecificDetails
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import no.elhub.auth.v1.features.requests.creation.EnergySupplierFrameworkAgreementRequestHandler as HandlerContract

private const val REQUEST_VALID_DAYS = 28

class EnergySupplierFrameworkAgreementRequestHandler(private val organisationsService: OrganisationsService) : HandlerContract {
    override suspend fun validateAndGetRequestTypeSpecificDetails(
        details: AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement,
        requestedBy: AuthorizationAgent,
        requestedPrincipal: AuthorizationAgent,
        requestedOf: AuthorizationAgent,
    ): RequestTypeSpecificDetails {
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

        val metadata = EnergySupplierFrameworkAgreementRequestMetadata(balanceSupplierName = balanceSupplierName, organizationName = organisationName)
        return RequestTypeSpecificDetails(
            validTo = Clock.System.now() + REQUEST_VALID_DAYS.days,
            meta = metadata.toJsonObject(),
        )
    }
}
