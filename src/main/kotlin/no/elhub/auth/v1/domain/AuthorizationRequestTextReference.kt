package no.elhub.auth.v1.domain

object AuthorizationRequestTextReference {
    fun forRequestType(requestType: AuthorizationRequestType): String =
        when (requestType) {
            AuthorizationRequestType.ChangeOfEnergySupplierForOrganization -> "v1"
            AuthorizationRequestType.MoveInAndChangeOfEnergySupplierForOrganization -> "v1"
            AuthorizationRequestType.EnergySupplierFrameworkAgreement -> "v1"
        }
}
