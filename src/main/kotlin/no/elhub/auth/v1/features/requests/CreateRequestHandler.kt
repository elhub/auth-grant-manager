package no.elhub.auth.v1.features.requests

import no.elhub.auth.v0.features.common.party.AuthorizationParty
import no.elhub.auth.v1.domain.AuthorizationRequest
import no.elhub.auth.v1.domain.requests.AuthorizationRequestSpecification
import no.elhub.auth.v1.domain.requests.AuthorizationRequestTypeDetails
import no.elhub.auth.v1.features.requests.creation.ChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.features.requests.creation.EnergySupplierFrameworkAgreementRequestHandler
import no.elhub.auth.v1.features.requests.creation.MoveInAndChangeOfEnergySupplierRequestHandler
import no.elhub.auth.v1.repositories.RequestRepository

class CreateRequestHandler(
    private val changeOfEnergySupplierHandler: ChangeOfEnergySupplierRequestHandler,
    private val moveInAndChangeOfEnergySupplierHandler: MoveInAndChangeOfEnergySupplierRequestHandler,
    private val energySupplierFrameworkAgreementHandler: EnergySupplierFrameworkAgreementRequestHandler,
    private val requestRepository: RequestRepository,
) {
    suspend operator fun invoke(
        specification: AuthorizationRequestSpecification,
        requestedBy: AuthorizationParty,
        requestedPrincipal: AuthorizationParty,
        requestedOf: AuthorizationParty,
    ): AuthorizationRequest {
        val requestTypeSpecificDetails = when (val details = specification.details) {
            is AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization ->
                changeOfEnergySupplierHandler.validateAndGetRequestTypeSpecificDetails(
                    details,
                    requestedBy,
                    requestedPrincipal,
                    requestedOf,
                )

            is AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization ->
                moveInAndChangeOfEnergySupplierHandler.validateAndGetRequestTypeSpecificDetails(
                    details,
                    requestedBy,
                    requestedPrincipal,
                    requestedOf,
                )

            is AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement ->
                energySupplierFrameworkAgreementHandler.validateAndGetRequestTypeSpecificDetails(
                    details,
                    requestedBy,
                    requestedPrincipal,
                    requestedOf,
                )
        }

        val request = AuthorizationRequest.create(
            requestedBy = requestedBy,
            requestedPrincipal = requestedPrincipal,
            requestedOf = requestedOf,
            details = specification.details,
            validTo = requestTypeSpecificDetails.validTo,
            meta = requestTypeSpecificDetails.meta,
            externalReference = specification.externalReference,
            redirectUri = specification.redirectUri,
        )
        return requestRepository.insert(request)
    }
}
