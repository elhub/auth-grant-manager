package no.elhub.auth.v1.acceptance.documents.frameworkagreement

import no.elhub.auth.v1.acceptance.support.Organization
import no.elhub.auth.v1.acceptance.support.osloStartOfDay

/** Builds the document body without registering actors or external-service responses. */
internal fun frameworkAgreementDocumentPayload(
    requestedPrincipal: Organization,
    requestedOf: Organization,
    language: String = "nb",
    externalReference: String? = null,
    validFrom: String = osloStartOfDay(daysFromNow = 30),
    validTo: String = osloStartOfDay(daysFromNow = 395),
): String {
    val reference = externalReference?.let { ",\n\"externalReference\": \"$it\"" }.orEmpty()
    return """
        {
          "data": {
            "type": "AuthorizationDocument",
            "attributes": {
              "documentType": "EnergySupplierFrameworkAgreement",
              "requestedGrantValidFrom": "$validFrom",
              "requestedGrantValidTo": "$validTo"$reference
            },
            "meta": {
              "requestedPrincipal": "${requestedPrincipal.number}",
              "requestedOf": "${requestedOf.number}",
              "language": "$language"
            }
          }
        }
    """
}
