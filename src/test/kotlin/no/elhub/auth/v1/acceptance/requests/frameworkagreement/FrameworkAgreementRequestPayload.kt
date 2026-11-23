package no.elhub.auth.v1.acceptance.requests.frameworkagreement

import no.elhub.auth.v1.acceptance.support.Organization
import no.elhub.auth.v1.acceptance.support.osloStartOfDay

/** Builds the request body without registering actors or external-service responses. */
internal fun frameworkAgreementRequestPayload(
    requestedPrincipal: Organization,
    requestedOf: Organization,
    redirectUri: String? = null,
    externalReference: String? = null,
    validFrom: String? = osloStartOfDay(daysFromNow = 30),
    validTo: String? = osloStartOfDay(daysFromNow = 395),
): String {
    val optional = listOfNotNull(
        validFrom?.let { "\"requestedGrantValidFrom\": \"$it\"" },
        validTo?.let { "\"requestedGrantValidTo\": \"$it\"" },
        redirectUri?.let { "\"redirectUri\": \"$it\"" },
        externalReference?.let { "\"externalReference\": \"$it\"" },
    ).joinToString("") { ",\n$it" }
    return """
        {
          "data": {
            "type": "AuthorizationRequest",
            "attributes": {
              "requestType": "EnergySupplierFrameworkAgreement"$optional
            },
            "meta": {
              "requestedPrincipal": "${requestedPrincipal.number}",
              "requestedOf": "${requestedOf.number}"
            }
          }
        }
    """
}
