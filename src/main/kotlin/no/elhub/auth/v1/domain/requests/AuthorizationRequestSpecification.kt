package no.elhub.auth.v1.domain.requests

import no.elhub.auth.v1.domain.AuthorizationRequestType

data class AuthorizationRequestSpecification(
    val externalReference: String?,
    val redirectUri: String?,
    val details: AuthorizationRequestTypeDetails,
) {
    val requestType: AuthorizationRequestType
        get() = when (details) {
            is AuthorizationRequestTypeDetails.ChangeOfEnergySupplierForOrganization ->
                AuthorizationRequestType.ChangeOfEnergySupplierForOrganization

            is AuthorizationRequestTypeDetails.MoveInAndChangeOfEnergySupplierForOrganization ->
                AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization

            is AuthorizationRequestTypeDetails.EnergySupplierFrameworkAgreement ->
                AuthorizationRequestType.EnergySupplierFrameworkAgreement
        }
}
